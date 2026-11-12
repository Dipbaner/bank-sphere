package com.nova.tech.bank.sphere.dto;

import com.nova.tech.bank.sphere.entity.Account;
import com.nova.tech.bank.sphere.entity.AccountStatus;
import com.nova.tech.bank.sphere.entity.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record AccountResponse(
        String accountNumber, String type, String status, BigDecimal balance, Instant createdAt) {
}
