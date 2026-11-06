package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.AuditAction;
import com.nova.tech.bank.sphere.entity.AuditStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TransactionAuditListener {

    private final AuditService auditService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(TransactionCompletedEvent e) {
        auditService.log(e.userId(), e.email(), AuditAction.TRANSACTION_COMPLETED, AuditStatus.SUCCESS,
                e.type() + " " + e.amount().toPlainString() + " " + e.referenceId());
    }
}