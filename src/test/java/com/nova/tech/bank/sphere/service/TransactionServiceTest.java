package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.StepUpRequiredException;
import com.nova.tech.bank.sphere.exception.TransactionException;
import com.nova.tech.bank.sphere.repository.AccountRepository;
import com.nova.tech.bank.sphere.repository.BankTransactionRepository;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.util.ReferenceIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final String NUMBER = "100000000001";
    private static final String KEY = "key-0001-abcdef";

    @Mock AccountRepository accountRepository;
    @Mock BankTransactionRepository transactionRepository;
    @Mock StepUpService stepUpService;
    @Mock AuditService auditService;
    @Mock ApplicationEventPublisher events;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);
    private final StepUpPolicy policy = new StepUpPolicy(new BigDecimal("10000.00"));
    private final AuthenticatedUser me = new AuthenticatedUser(1L, "dip@example.com", "jti", Instant.now());

    private TransactionService service;
    private Account account;

    @BeforeEach
    void setUp() {
        service = new TransactionService(accountRepository, transactionRepository,
                new ReferenceIdGenerator(clock), stepUpService, policy, auditService, events, clock);
        ReflectionTestUtils.setField(service, "maxAmount", new BigDecimal("1000000.00"));
        ReflectionTestUtils.setField(service, "dailyDebitLimit", new BigDecimal("100000.00"));

        account = account(AccountStatus.ACTIVE, "1000.00");
        lenient().when(accountRepository.lockByAccountNumberAndUserId(NUMBER, 1L)).thenReturn(Optional.of(account));
        lenient().when(transactionRepository.debitedSince(eq(10L), any())).thenReturn(BigDecimal.ZERO);
        lenient().when(transactionRepository.saveAndFlush(any(BankTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private static Account account(AccountStatus status, String balance) {
        return Account.builder().id(10L).accountNumber(NUMBER).userId(1L).type(AccountType.SAVINGS)
                .status(status).balance(new BigDecimal(balance)).build();
    }

    private BankTransaction saved() {
        ArgumentCaptor<BankTransaction> captor = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static TransactionException failure(Runnable action) {
        return assertThrows(TransactionException.class, action::run);
    }

    /* =============================================================== deposit */

    @Test
    void deposit_increasesTheBalance_andWritesOneLedgerRow() {
        var result = service.deposit(me, NUMBER, new BigDecimal("250.5"), "  Cash  ", KEY);

        assertEquals("1250.50", account.getBalance().toPlainString());
        assertFalse(result.replayed());

        BankTransaction t = saved();
        assertEquals(TransactionType.DEPOSIT, t.getType());
        assertEquals(TransactionStatus.SUCCESS, t.getStatus());
        assertEquals("250.50", t.getAmount().toPlainString());
        assertEquals(10L, t.getToAccountId());
        assertNull(t.getFromAccountId());
        assertEquals("1250.50", t.getToBalanceAfter().toPlainString());
        assertEquals("Cash", t.getDescription());
        assertEquals(KEY, t.getIdempotencyKey());
        assertTrue(t.getReferenceId().startsWith("TXN20261023-"));
        verify(events).publishEvent(any(TransactionCompletedEvent.class));
    }

    @Test
    void deposit_repeatedRequest_returnsTheOriginal_andMovesNoMoney() {
        BigDecimal amount = new BigDecimal("100.00");
        BankTransaction original = BankTransaction.builder()
                .referenceId("TXN20261023-ABCDEFGH").type(TransactionType.DEPOSIT).status(TransactionStatus.SUCCESS)
                .amount(amount).toAccountId(10L).toAccountNumber(NUMBER).toBalanceAfter(new BigDecimal("1100.00"))
                .userId(1L).idempotencyKey(KEY).createdAt(LocalDateTime.now(clock))
                .requestFingerprint(TransactionService.fingerprint(TransactionType.DEPOSIT, NUMBER, amount))
                .build();
        when(transactionRepository.findByUserIdAndIdempotencyKey(1L, KEY)).thenReturn(Optional.of(original));

        var result = service.deposit(me, NUMBER, new BigDecimal("100"), null, KEY);

        assertTrue(result.replayed());
        assertSame(original, result.transaction());
        assertEquals("1000.00", account.getBalance().toPlainString());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void sameKeyForADifferentAmount_isRefused() {
        BankTransaction original = BankTransaction.builder().userId(1L).idempotencyKey(KEY)
                .requestFingerprint(TransactionService.fingerprint(TransactionType.DEPOSIT, NUMBER, new BigDecimal("100.00")))
                .build();
        when(transactionRepository.findByUserIdAndIdempotencyKey(1L, KEY)).thenReturn(Optional.of(original));

        TransactionException ex = failure(() -> service.deposit(me, NUMBER, new BigDecimal("999"), null, KEY));

        assertEquals("IDEMPOTENCY_KEY_REUSED", ex.getCode());
        assertEquals(409, ex.getStatus().value());
        assertEquals("1000.00", account.getBalance().toPlainString());
    }

    @Test
    void missingOrMalformedKey_isRefused_beforeAnythingElse() {
        for (String bad : new String[]{null, "", "short", "has spaces in it!", "x".repeat(65)}) {
            TransactionException ex = failure(() -> service.deposit(me, NUMBER, new BigDecimal("10"), null, bad));
            assertEquals("IDEMPOTENCY_KEY_REQUIRED", ex.getCode());
        }
        verifyNoInteractions(accountRepository);
    }

    @Test
    void invalidAmounts_areRefused_andNothingIsLocked() {
        for (String bad : new String[]{"0", "-5", "0.001", "10.505", "1000000.01"}) {
            TransactionException ex = failure(() -> service.deposit(me, NUMBER, new BigDecimal(bad), null, KEY));
            assertEquals("INVALID_AMOUNT", ex.getCode());
        }
        assertEquals("INVALID_AMOUNT", failure(() -> service.deposit(me, NUMBER, null, null, KEY)).getCode());
        verifyNoInteractions(accountRepository);
    }

    @Test
    void someoneElsesOrUnknownAccount_looksLikeNotFound() {
        when(accountRepository.lockByAccountNumberAndUserId("100000000002", 1L)).thenReturn(Optional.empty());

        TransactionException ex = failure(() -> service.deposit(me, "100000000002", new BigDecimal("10"), null, KEY));

        assertEquals("ACCOUNT_NOT_FOUND", ex.getCode());
        assertEquals(404, ex.getStatus().value());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void badAccountNumberFormat_isNotFound_withoutTouchingTheDatabase() {
        for (String bad : new String[]{"abc", "12", "1234567890123456789012", "100000000001; DROP TABLE"}) {
            assertEquals("ACCOUNT_NOT_FOUND",
                    failure(() -> service.deposit(me, bad, new BigDecimal("10"), null, KEY)).getCode());
        }
        verifyNoInteractions(accountRepository);
    }

    @Test
    void frozenOrClosedAccount_cannotTransact_andTheRefusalIsAudited() {
        for (AccountStatus status : new AccountStatus[]{AccountStatus.FROZEN, AccountStatus.CLOSED, AccountStatus.PENDING}) {
            account.setStatus(status);
            TransactionException ex = failure(() -> service.deposit(me, NUMBER, new BigDecimal("10"), null, KEY));
            assertEquals("ACCOUNT_NOT_ACTIVE", ex.getCode());
        }
        assertEquals("1000.00", account.getBalance().toPlainString());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(auditService, times(3)).log(eq(1L), any(), eq(AuditAction.TRANSACTION_REJECTED),
                eq(AuditStatus.FAILURE), any());
    }

    /* ============================================================== withdraw */

    @Test
    void withdraw_decreasesTheBalance_andWritesOneLedgerRow() {
        service.withdraw(me, NUMBER, new BigDecimal("400.25"), null, KEY, null);

        assertEquals("599.75", account.getBalance().toPlainString());
        BankTransaction t = saved();
        assertEquals(TransactionType.WITHDRAWAL, t.getType());
        assertEquals(10L, t.getFromAccountId());
        assertNull(t.getToAccountId());
        assertEquals("599.75", t.getFromBalanceAfter().toPlainString());
        verify(stepUpService, never()).require(any(), any(), any(), any());
    }

    @Test
    void withdraw_theWholeBalance_isAllowed() {
        service.withdraw(me, NUMBER, new BigDecimal("1000.00"), null, KEY, null);
        assertEquals("0.00", account.getBalance().toPlainString());
    }

    @Test
    void withdraw_moreThanTheBalance_isRefused_andNothingChanges() {
        TransactionException ex = failure(() -> service.withdraw(me, NUMBER, new BigDecimal("1000.01"), null, KEY, null));

        assertEquals("INSUFFICIENT_FUNDS", ex.getCode());
        assertEquals(422, ex.getStatus().value());
        assertEquals("1000.00", account.getBalance().toPlainString());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(auditService).log(eq(1L), any(), eq(AuditAction.TRANSACTION_REJECTED), eq(AuditStatus.FAILURE), any());
    }

    @Test
    void withdraw_dailyLimit_isEnforced_andTheLimitItselfIsAllowed() {
        account.setBalance(new BigDecimal("500000.00"));
        when(transactionRepository.debitedSince(eq(10L), any())).thenReturn(new BigDecimal("99900.00"));

        service.withdraw(me, NUMBER, new BigDecimal("100.00"), null, KEY, null);      // exactly reaches 100000.00
        assertEquals("499900.00", account.getBalance().toPlainString());

        TransactionException ex = failure(() ->
                service.withdraw(me, NUMBER, new BigDecimal("100.01"), null, "key-0002-abcdef", null));
        assertEquals("DAILY_LIMIT_EXCEEDED", ex.getCode());
        assertEquals("499900.00", account.getBalance().toPlainString());
    }

    @Test
    void withdraw_highValue_withoutApproval_asksForStepUp_andChangesNothing() {
        account.setBalance(new BigDecimal("50000.00"));
        doThrow(new StepUpRequiredException(StepUpAction.HIGH_VALUE_WITHDRAWAL, "acct=" + NUMBER + ";amount=15000.00"))
                .when(stepUpService).require(eq(1L), eq(StepUpAction.HIGH_VALUE_WITHDRAWAL), any(), any());

        StepUpRequiredException ex = assertThrows(StepUpRequiredException.class,
                () -> service.withdraw(me, NUMBER, new BigDecimal("15000"), null, KEY, null));

        assertEquals("acct=" + NUMBER + ";amount=15000.00", ex.getContext());
        assertEquals("50000.00", account.getBalance().toPlainString());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void withdraw_highValue_withApproval_goesThrough_boundToThisAccountAndAmount() {
        account.setBalance(new BigDecimal("50000.00"));

        service.withdraw(me, NUMBER, new BigDecimal("15000"), null, KEY, "approved-token");

        verify(stepUpService).require(1L, StepUpAction.HIGH_VALUE_WITHDRAWAL, "approved-token",
                "acct=" + NUMBER + ";amount=15000.00");
        assertEquals("35000.00", account.getBalance().toPlainString());
    }

    @Test
    void withdraw_thatCannotSucceed_neverAsksForACode() {
        failure(() -> service.withdraw(me, NUMBER, new BigDecimal("20000"), null, KEY, null));
        verify(stepUpService, never()).require(any(), any(), any(), any());
    }

    @Test
    void withdraw_repeatedRequest_returnsTheOriginal_withoutAnotherCode() {
        BigDecimal amount = new BigDecimal("15000.00");
        BankTransaction original = BankTransaction.builder()
                .referenceId("TXN20261023-ABCDEFGH").type(TransactionType.WITHDRAWAL).status(TransactionStatus.SUCCESS)
                .amount(amount).fromAccountId(10L).fromAccountNumber(NUMBER).fromBalanceAfter(new BigDecimal("35000.00"))
                .userId(1L).idempotencyKey(KEY).createdAt(LocalDateTime.now(clock))
                .requestFingerprint(TransactionService.fingerprint(TransactionType.WITHDRAWAL, NUMBER, amount))
                .build();
        when(transactionRepository.findByUserIdAndIdempotencyKey(1L, KEY)).thenReturn(Optional.of(original));

        var result = service.withdraw(me, NUMBER, new BigDecimal("15000"), null, KEY, null);

        assertTrue(result.replayed());
        verify(stepUpService, never()).require(any(), any(), any(), any());
    }
}