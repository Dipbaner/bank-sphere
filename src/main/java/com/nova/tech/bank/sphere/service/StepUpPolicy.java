package com.nova.tech.bank.sphere.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StepUpPolicy {

    private final BigDecimal threshold;

    public StepUpPolicy(@Value("${app.stepup.transfer-threshold}") BigDecimal threshold) {
        this.threshold = threshold;
    }

    /** Amounts ABOVE the threshold need a fresh OTP. */
    public boolean transferNeedsStepUp(BigDecimal amount) {
        return amount.compareTo(threshold) > 0;
    }

    public boolean withdrawalNeedsStepUp(BigDecimal amount) {
        return amount.compareTo(threshold) > 0;
    }

    /** Same text for the same transfer, so "100" and "100.00" give one context. */
    public static String transferContext(String toAccount, BigDecimal amount) {
        return "to=" + toAccount + ";amount=" + scaled(amount);
    }

    public static String withdrawalContext(String accountNumber, BigDecimal amount) {
        return "acct=" + accountNumber + ";amount=" + scaled(amount);
    }

    private static String scaled(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}