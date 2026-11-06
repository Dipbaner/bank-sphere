package com.nova.tech.bank.sphere.dto;

import com.nova.tech.bank.sphere.entity.BankTransaction;
import com.nova.tech.bank.sphere.entity.TransactionStatus;
import com.nova.tech.bank.sphere.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** What the customer sees, built from the point of view of ONE of their accounts. */
public record TransactionResponse(
        String referenceId,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String accountNumber,
        String counterpartyAccountNumber,   // masked; null for deposits and withdrawals
        BigDecimal balanceAfter,
        String description,
        LocalDateTime createdAt) {

    public static TransactionResponse forAccount(BankTransaction t, String accountNumber) {
        boolean outgoing = accountNumber.equals(t.getFromAccountNumber());
        String other = outgoing ? t.getToAccountNumber() : t.getFromAccountNumber();
        return new TransactionResponse(
                t.getReferenceId(), t.getType(), t.getStatus(), t.getAmount(),
                accountNumber,
                other == null ? null : "****" + other.substring(Math.max(0, other.length() - 4)),
                outgoing ? t.getFromBalanceAfter() : t.getToBalanceAfter(),
                t.getDescription(), t.getCreatedAt());
    }
}