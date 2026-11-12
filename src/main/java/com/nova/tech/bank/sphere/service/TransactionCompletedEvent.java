package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.TransactionType;

import java.math.BigDecimal;

/** Published inside the money transaction; handled only after it commits. */
public record TransactionCompletedEvent(
        Long userId, String referenceId, String type, BigDecimal amount, String accountNumber) {
}