package com.nova.tech.bank.sphere.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        String referenceId,
        String type,
        String status,
        BigDecimal amount,
        String accountNumber,
        String counterpartyAccountNumber,
        BigDecimal balanceAfter,
        String description,
        LocalDateTime createdAt) {
}