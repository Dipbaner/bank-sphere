package com.nova.tech.bank.sphere.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class StepUpPolicyTest {

    private final StepUpPolicy policy = new StepUpPolicy(new BigDecimal("10000.00"));

    @Test
    void amountsAtOrBelowTheThreshold_doNotNeedStepUp() {
        assertFalse(policy.transferNeedsStepUp(new BigDecimal("500")));
        assertFalse(policy.transferNeedsStepUp(new BigDecimal("10000")));
        assertFalse(policy.transferNeedsStepUp(new BigDecimal("10000.00")));
    }

    @Test
    void amountsAboveTheThreshold_needStepUp() {
        assertTrue(policy.transferNeedsStepUp(new BigDecimal("10000.01")));
        assertTrue(policy.transferNeedsStepUp(new BigDecimal("50000")));
    }

    @Test
    void context_isIdenticalForEquivalentAmounts() {
        assertEquals(StepUpPolicy.transferContext("ACC-1", new BigDecimal("15000")),
                StepUpPolicy.transferContext("ACC-1", new BigDecimal("15000.00")));
        assertEquals("to=ACC-1;amount=15000.00",
                StepUpPolicy.transferContext("ACC-1", new BigDecimal("15000")));
    }

    @Test
    void context_changesWithRecipientAndAmount() {
        String base = StepUpPolicy.transferContext("ACC-1", new BigDecimal("15000"));
        assertNotEquals(base, StepUpPolicy.transferContext("ACC-2", new BigDecimal("15000")));
        assertNotEquals(base, StepUpPolicy.transferContext("ACC-1", new BigDecimal("15001")));
    }

    @Test
    void withdrawalsUseTheSameThreshold() {
        assertFalse(policy.withdrawalNeedsStepUp(new BigDecimal("10000.00")));
        assertTrue(policy.withdrawalNeedsStepUp(new BigDecimal("10000.01")));
    }

    @Test
    void withdrawalContext_isStable() {
        assertEquals("acct=100000000001;amount=15000.00",
                StepUpPolicy.withdrawalContext("100000000001", new BigDecimal("15000")));
    }
}