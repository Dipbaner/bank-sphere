package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.StepUpAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StepUpPolicy {

    private final BigDecimal threshold;

    public StepUpPolicy(@Value("${app.stepup.transfer-threshold:10000.00}") BigDecimal threshold) {
        this.threshold = threshold;
    }

    /** Strictly above the threshold needs a fresh OTP; exactly the threshold does not. */
    public boolean requiresStepUp(BigDecimal amount) {
        return amount.compareTo(threshold) > 0;
    }

    public StepUpAction withdrawalAction() {
        return StepUpAction.HIGH_VALUE_WITHDRAWAL;
    }

    /** Stable context string the approval is bound to. */
    public String withdrawalContext(String accountNumber, BigDecimal amount) {
        return "acct=" + accountNumber + ";amount=" + amount.toPlainString();
    }
}
