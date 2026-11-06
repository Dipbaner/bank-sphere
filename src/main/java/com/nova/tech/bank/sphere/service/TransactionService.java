package com.nova.tech.bank.sphere.service;

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

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^[0-9]{6,20}$");

    // The order of these fields is the constructor order. The unit test relies on it.
    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final ReferenceIdGenerator referenceIds;
    private final StepUpService stepUpService;
    private final StepUpPolicy stepUpPolicy;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Value("${app.transaction.max-amount}")
    private BigDecimal maxAmount;

    @Value("${app.transaction.daily-debit-limit}")
    private BigDecimal dailyDebitLimit;

    /** The ledger row, the account it is seen from, and whether it repeats an earlier request. */
    public record Result(BankTransaction transaction, String accountNumber, boolean replayed) {}

    /* ============================================================ deposit */

    @Transactional
    public Result deposit(AuthenticatedUser me, String accountNumber, BigDecimal rawAmount,
                          String description, String idempotencyKey) {

        String key = requireKey(idempotencyKey);
        BigDecimal amount = Money.requireValid(rawAmount, maxAmount);

        // FIRST database access: it locks the row, so everything after it sees the latest data.
        Account account = lockOwnAccount(me, accountNumber);

        String fingerprint = fingerprint(TransactionType.DEPOSIT, account.getAccountNumber(), amount);
        Result replay = replayOf(me, key, fingerprint, account.getAccountNumber());
        if (replay != null) return replay;

        requireActive(me, account);

        BigDecimal after = account.getBalance().add(amount);
        account.setBalance(after);
        accountRepository.save(account);

        BankTransaction saved = persist(base(TransactionType.DEPOSIT, me, amount, description, key, fingerprint)
                .toAccountId(account.getId())
                .toAccountNumber(account.getAccountNumber())
                .toBalanceAfter(after)
                .build());

        return completed(me, saved, account.getAccountNumber());
    }

    /* =========================================================== withdraw */

    @Transactional
    public Result withdraw(AuthenticatedUser me, String accountNumber, BigDecimal rawAmount,
                           String description, String idempotencyKey, String stepUpToken) {

        String key = requireKey(idempotencyKey);
        BigDecimal amount = Money.requireValid(rawAmount, maxAmount);

        Account account = lockOwnAccount(me, accountNumber);

        String fingerprint = fingerprint(TransactionType.WITHDRAWAL, account.getAccountNumber(), amount);
        Result replay = replayOf(me, key, fingerprint, account.getAccountNumber());
        if (replay != null) return replay;

        requireActive(me, account);

        if (account.getBalance().compareTo(amount) < 0) {
            throw reject(me, "INSUFFICIENT_FUNDS", "Insufficient balance for this withdrawal.",
                    "withdrawal " + amount.toPlainString());
        }

        BigDecimal debitedToday = transactionRepository.debitedSince(account.getId(), startOfToday());
        if (debitedToday.add(amount).compareTo(dailyDebitLimit) > 0) {
            BigDecimal remaining = dailyDebitLimit.subtract(debitedToday).max(BigDecimal.ZERO);
            throw reject(me, "DAILY_LIMIT_EXCEEDED",
                    "Daily limit reached. You can still move " + remaining.toPlainString() + " today.",
                    "withdrawal " + amount.toPlainString());
        }

        // Last check, so a request that cannot succeed never asks for a code. The approval is consumed
        // inside THIS transaction: if anything below fails, the rollback makes it usable again.
        if (stepUpPolicy.withdrawalNeedsStepUp(amount)) {
            stepUpService.require(me.id(), StepUpAction.HIGH_VALUE_WITHDRAWAL, stepUpToken,
                    StepUpPolicy.withdrawalContext(account.getAccountNumber(), amount));
        }

        BigDecimal after = account.getBalance().subtract(amount);
        account.setBalance(after);
        accountRepository.save(account);

        BankTransaction saved = persist(base(TransactionType.WITHDRAWAL, me, amount, description, key, fingerprint)
                .fromAccountId(account.getId())
                .fromAccountNumber(account.getAccountNumber())
                .fromBalanceAfter(after)
                .build());

        return completed(me, saved, account.getAccountNumber());
    }

    /* ========================================================== helpers */

    private String requireKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new TransactionException("IDEMPOTENCY_KEY_REQUIRED",
                    "Send an Idempotency-Key header of 8 to 64 letters, digits, '-' or '_'.",
                    HttpStatus.BAD_REQUEST);
        }
        return key;
    }

    private Account lockOwnAccount(AuthenticatedUser me, String accountNumber) {
        if (accountNumber == null || !ACCOUNT_NUMBER.matcher(accountNumber).matches()) {
            throw notFound();
        }
        // Someone else's account gives the same answer as an unknown one: no guessing of account numbers.
        return accountRepository.lockByAccountNumberAndUserId(accountNumber, me.id())
                .orElseThrow(this::notFound);
    }

    private TransactionException notFound() {
        return new TransactionException("ACCOUNT_NOT_FOUND", "Account not found.", HttpStatus.NOT_FOUND);
    }

    private void requireActive(AuthenticatedUser me, Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw reject(me, "ACCOUNT_NOT_ACTIVE",
                    "This account is not active, so it cannot be used for transactions.",
                    "account status " + account.getStatus());
        }
    }

    /** An identical request returns the stored result. The same key for a different request is refused. */
    private Result replayOf(AuthenticatedUser me, String key, String fingerprint, String accountNumber) {
        return transactionRepository.findByUserIdAndIdempotencyKey(me.id(), key)
                .map(existing -> {
                    if (!existing.getRequestFingerprint().equals(fingerprint)) {
                        throw new TransactionException("IDEMPOTENCY_KEY_REUSED",
                                "This Idempotency-Key was already used for a different request.",
                                HttpStatus.CONFLICT);
                    }
                    return new Result(existing, accountNumber, true);
                })
                .orElse(null);
    }

    private BankTransaction.BankTransactionBuilder base(TransactionType type, AuthenticatedUser me,
                                                        BigDecimal amount, String description, String key, String fingerprint) {
        return BankTransaction.builder()
                .referenceId(referenceIds.next())
                .type(type)
                .status(TransactionStatus.SUCCESS)
                .amount(amount)
                .description(cleanDescription(description))
                .userId(me.id())
                .idempotencyKey(key)
                .requestFingerprint(fingerprint)
                .createdAt(LocalDateTime.now(clock));
    }

    /** The unique constraints are the last line of defence. A clash rolls the whole transaction back. */
    private BankTransaction persist(BankTransaction transaction) {
        try {
            return transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            throw new TransactionException("REQUEST_IN_PROGRESS",
                    "The same request is already being processed. Retry with the same Idempotency-Key.",
                    HttpStatus.CONFLICT);
        }
    }

    private Result completed(AuthenticatedUser me, BankTransaction saved, String accountNumber) {
        events.publishEvent(new TransactionCompletedEvent(me.id(), me.email(), saved.getReferenceId(),
                saved.getType(), saved.getAmount()));          // audited only AFTER the commit
        return new Result(saved, accountNumber, false);
    }

    /** Audits the refusal (in its own transaction, so it survives the rollback) and returns the exception. */
    private TransactionException reject(AuthenticatedUser me, String code, String message, String details) {
        auditService.log(me.id(), me.email(), AuditAction.TRANSACTION_REJECTED, AuditStatus.FAILURE,
                code + ": " + details);
        return new TransactionException(code, message);
    }

    private LocalDateTime startOfToday() {
        return LocalDate.now(clock).atStartOfDay();
    }

    private static String cleanDescription(String description) {
        if (description == null) return null;
        String clean = description.replaceAll("\\p{Cntrl}", " ").trim();
        if (clean.isEmpty()) return null;
        return clean.length() <= 140 ? clean : clean.substring(0, 140);
    }

    /** Identifies WHAT was asked (type, account, amount). Package-private so tests can reuse it. */
    static String fingerprint(TransactionType type, String accountNumber, BigDecimal amount) {
        return TokenUtil.sha256(type + "|" + accountNumber + "|" + amount.toPlainString());
    }
}