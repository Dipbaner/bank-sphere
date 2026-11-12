package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.TransactionResponse;
import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.TransactionException;
import com.nova.tech.bank.sphere.repository.AccountRepository;
import com.nova.tech.bank.sphere.repository.BankTransactionRepository;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.util.Money;
import com.nova.tech.bank.sphere.util.ReferenceIdGenerator;
import com.nova.tech.bank.sphere.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    public record Outcome(TransactionResponse response, boolean replayed) { }

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final Set<String> AUDITED_REFUSALS =
            Set.of("INSUFFICIENT_FUNDS", "DAILY_LIMIT_EXCEEDED", "ACCOUNT_NOT_ACTIVE");

    private final AccountRepository accounts;
    private final BankTransactionRepository ledger;
    private final StepUpPolicy stepUpPolicy;
    private final StepUpService stepUpService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final BigDecimal maxAmount;
    private final BigDecimal dailyDebitLimit;

    public TransactionService(AccountRepository accounts, BankTransactionRepository ledger,
                              StepUpPolicy stepUpPolicy, StepUpService stepUpService,
                              AuditService auditService, ApplicationEventPublisher events, Clock clock,
                              @Value("${app.transaction.max-amount:1000000.00}") BigDecimal maxAmount,
                              @Value("${app.transaction.daily-debit-limit:100000.00}") BigDecimal dailyDebitLimit) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.stepUpPolicy = stepUpPolicy;
        this.stepUpService = stepUpService;
        this.auditService = auditService;
        this.events = events;
        this.clock = clock;
        this.maxAmount = maxAmount;
        this.dailyDebitLimit = dailyDebitLimit;
    }

    @Transactional
    public Outcome deposit(AuthenticatedUser user, String accountNumber, String key, MoneyRequest req) {
        return execute(user, accountNumber, key, req, TransactionType.DEPOSIT, null);
    }

    @Transactional
    public Outcome withdraw(AuthenticatedUser user, String accountNumber, String key, MoneyRequest req,
                            String stepUpToken) {
        return execute(user, accountNumber, key, req, TransactionType.WITHDRAWAL, stepUpToken);
    }

    private Outcome execute(AuthenticatedUser user, String accountNumber, String key, MoneyRequest req,
                            TransactionType type, String stepUpToken) {
        try {
            return doExecute(user, accountNumber, key, req, type, stepUpToken);
        } catch (TransactionException e) {
            if (AUDITED_REFUSALS.contains(e.getCode())) {
                // AuditService must use REQUIRES_NEW so this survives the rollback.
                auditService.record(AuditAction.TRANSACTION_REJECTED, user.id(),
                        type + " code=" + e.getCode() + " account=" + accountNumber);
            }
            throw e;
        }
    }

    private Outcome doExecute(AuthenticatedUser user, String accountNumber, String key, MoneyRequest req,
                              TransactionType type, String stepUpToken) {
        // 1-2. Validate key and amount
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new TransactionException("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required (8-64 letters, digits, - or _).");
        }
        BigDecimal amount = Money.requireValid(req.amount(), maxAmount);

        // 3. FIRST database statement: lock the caller's own account
        Account account;
        try {
            account = accounts.lockByAccountNumberAndUserId(accountNumber, user.id())
                    .orElseThrow(() -> new TransactionException("ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "Account not found."));
        } catch (PessimisticLockingFailureException e) {
            throw new TransactionException("ACCOUNT_BUSY", HttpStatus.CONFLICT,
                    "Account is busy. Retry with the same Idempotency-Key.");
        }

        // 4. Fingerprint and replay
        String fingerprint = TokenUtil.sha256Hex(type + "|" + accountNumber + "|" + amount.toPlainString());
        var existing = ledger.findByUserIdAndIdempotencyKey(user.id(), key);
        if (existing.isPresent()) {
            BankTransaction old = existing.get();
            if (!fingerprint.equals(old.getRequestFingerprint())) {
                throw new TransactionException("IDEMPOTENCY_KEY_REUSED", HttpStatus.CONFLICT,
                        "This Idempotency-Key was used for a different request.");
            }
            return new Outcome(toResponse(old, accountNumber), true);
        }

        // 5. Account must be ACTIVE
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new TransactionException("ACCOUNT_NOT_ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Account is not active.");
        }

        // 6-9. Type specific rules, then move the money
        BigDecimal newBalance;
        if (type == TransactionType.DEPOSIT) {
            newBalance = account.getBalance().add(amount);
        } else {
            if (account.getBalance().compareTo(amount) < 0) {
                throw new TransactionException("INSUFFICIENT_FUNDS", HttpStatus.UNPROCESSABLE_ENTITY,
                        "Insufficient funds.");
            }
            Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
            BigDecimal spentToday = ledger.sumDebitsSince(account.getId(), startOfDay);
            if (spentToday.add(amount).compareTo(dailyDebitLimit) > 0) {
                BigDecimal left = dailyDebitLimit.subtract(spentToday).max(BigDecimal.ZERO);
                throw new TransactionException("DAILY_LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY,
                        "Daily limit exceeded. You can still withdraw " + left.toPlainString() + " today.");
            }
            if (stepUpPolicy.requiresStepUp(amount)) {
                String context = stepUpPolicy.withdrawalContext(accountNumber, amount);
                if (stepUpToken == null || stepUpToken.isBlank()) {
                    throw new StepUpRequiredException(stepUpPolicy.withdrawalAction(), context);
                }
                // Consumed inside this transaction: any later failure rolls the approval back to usable.
                stepUpService.consume(user.id(), stepUpPolicy.withdrawalAction(), context, stepUpToken);
            }
            newBalance = account.getBalance().subtract(amount);
        }

        account.setBalance(newBalance);
        accounts.save(account);

        BankTransaction tx = new BankTransaction();
        tx.setReferenceId(ReferenceIdGenerator.generate(clock));
        tx.setType(type);
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setAmount(amount);
        if (type == TransactionType.DEPOSIT) {
            tx.setToAccountId(account.getId());
            tx.setToAccountNumber(accountNumber);
            tx.setToBalanceAfter(newBalance);
        } else {
            tx.setFromAccountId(account.getId());
            tx.setFromAccountNumber(accountNumber);
            tx.setFromBalanceAfter(newBalance);
        }
        tx.setDescription(clean(req.description()));
        tx.setUserId(user.id());
        tx.setIdempotencyKey(key);
        tx.setRequestFingerprint(fingerprint);
        tx.setCreatedAt(Instant.now(clock));
        try {
            tx = ledger.saveAndFlush(tx);
        } catch (DataIntegrityViolationException e) {
            // Unique (user_id, idempotency_key) or reference clash: last guard, whole transaction rolls back.
            throw new TransactionException("REQUEST_IN_PROGRESS", HttpStatus.CONFLICT,
                    "A duplicate request is in progress. Retry with the same Idempotency-Key.");
        }

        events.publishEvent(new TransactionCompletedEvent(
                user.id(), tx.getReferenceId(), type.name(), amount, accountNumber));
        return new Outcome(toResponse(tx, accountNumber), false);
    }

    private TransactionResponse toResponse(BankTransaction t, String accountNumber) {
        boolean deposit = t.getType() == TransactionType.DEPOSIT;
        return new TransactionResponse(t.getReferenceId(), t.getType().name(), t.getStatus().name(),
                t.getAmount(), accountNumber, null,
                deposit ? t.getToBalanceAfter() : t.getFromBalanceAfter(),
                t.getDescription(), t.getCreatedAt());
    }

    /** Strips control characters and trims to 140 characters. */
    private static String clean(String s) {
        if (s == null) return null;
        String c = s.replaceAll("\\p{Cntrl}", "").trim();
        if (c.length() > 140) c = c.substring(0, 140);
        return c.isEmpty() ? null : c;
    }
}

}