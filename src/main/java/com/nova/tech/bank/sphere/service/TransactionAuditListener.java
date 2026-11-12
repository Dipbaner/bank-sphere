package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.AuditAction;
import com.nova.tech.bank.sphere.entity.AuditStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TransactionAuditListener {

    private final AuditService auditService;

    public TransactionAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    /** Runs after commit, so the log never claims a rolled-back transaction. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(TransactionCompletedEvent e) {
        // Details: action, amount, reference only. Never tokens or OTPs.
        auditService.record(AuditAction.TRANSACTION_COMPLETED, e.userId(),
                e.type() + " amount=" + e.amount().toPlainString() + " ref=" + e.referenceId());
    }
}