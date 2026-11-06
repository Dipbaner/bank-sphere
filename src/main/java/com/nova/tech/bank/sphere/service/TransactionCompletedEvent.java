package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.TransactionType;

import java.math.BigDecimal;

public record TransactionCompletedEvent(Long userId, String email, String referenceId,
                                        TransactionType type, BigDecimal amount) {}