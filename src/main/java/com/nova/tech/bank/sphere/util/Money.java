package com.nova.tech.bank.sphere.util;

import com.nova.tech.bank.sphere.exception.TransactionException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    private static final BigDecimal MIN = new BigDecimal("0.01");

    private Money() {}

    /** Checks an amount and returns it with exactly two decimals. Never rounds silently. */
    public static BigDecimal requireValid(BigDecimal amount, BigDecimal max) {
        if (amount == null || amount.compareTo(MIN) < 0) {
            throw invalid("Amount must be at least 0.01");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw invalid("Amount can have at most 2 decimal places");
        }
        if (amount.compareTo(max) > 0) {
            throw invalid("Amount is above the maximum allowed per transaction");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);   // safe: scale was checked above
    }

    private static TransactionException invalid(String message) {
        return new TransactionException("INVALID_AMOUNT", message, HttpStatus.BAD_REQUEST);
    }
}