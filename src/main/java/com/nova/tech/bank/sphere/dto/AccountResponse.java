package com.nova.tech.bank.sphere.dto;

import com.nova.tech.bank.sphere.entity.Account;
import com.nova.tech.bank.sphere.entity.AccountStatus;
import com.nova.tech.bank.sphere.entity.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(String accountNumber, AccountType type, AccountStatus status,
                              BigDecimal balance, LocalDateTime createdAt) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getAccountNumber(), a.getType(), a.getStatus(),
                a.getBalance(), a.getCreatedAt());
    }
}