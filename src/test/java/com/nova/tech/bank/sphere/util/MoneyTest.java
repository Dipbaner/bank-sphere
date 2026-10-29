package com.nova.tech.bank.sphere.util;

import com.nova.tech.bank.sphere.exception.TransactionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    private static final BigDecimal MAX = new BigDecimal("1000000.00");

    @Test
    void validAmounts_areReturnedWithTwoDecimals() {
        assertEquals("100.00", Money.requireValid(new BigDecimal("100"), MAX).toPlainString());
        assertEquals("10.50", Money.requireValid(new BigDecimal("10.5"), MAX).toPlainString());
        assertEquals("0.01", Money.requireValid(new BigDecimal("0.01"), MAX).toPlainString());
        assertEquals("100.00", Money.requireValid(new BigDecimal("1E+2"), MAX).toPlainString());
        assertEquals("1000000.00", Money.requireValid(new BigDecimal("1000000"), MAX).toPlainString());
    }

    @Test
    void zeroNegativeAndNullAreRejected() {
        for (BigDecimal bad : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-5"), new BigDecimal("0.001")}) {
            TransactionException ex = assertThrows(TransactionException.class, () -> Money.requireValid(bad, MAX));
            assertEquals("INVALID_AMOUNT", ex.getCode());
            assertEquals(400, ex.getStatus().value());
        }
    }

    @Test
    void moreThanTwoDecimals_isRejected_notRounded() {
        assertThrows(TransactionException.class, () -> Money.requireValid(new BigDecimal("10.505"), MAX));
        assertThrows(TransactionException.class, () -> Money.requireValid(new BigDecimal("0.015"), MAX));
    }

    @Test
    void aboveTheMaximum_isRejected() {
        assertThrows(TransactionException.class, () -> Money.requireValid(new BigDecimal("1000000.01"), MAX));
    }

    @Test
    void trailingZerosBeyondTwoDecimals_areFine() {
        assertEquals("10.50", Money.requireValid(new BigDecimal("10.5000"), MAX).toPlainString());
    }
}