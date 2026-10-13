package com.nova.tech.bank.sphere.dto;

import com.nova.tech.bank.sphere.entity.AuditAction;
import com.nova.tech.bank.sphere.entity.AuditLog;
import com.nova.tech.bank.sphere.entity.AuditStatus;

import java.time.LocalDateTime;

public record AuditLogResponse(Long id, Long userId, String identifier, AuditAction action,
                               AuditStatus status, String ip, String userAgent,
                               String details, LocalDateTime eventTime) {

    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getUserId(), a.getIdentifier(), a.getAction(),
                a.getStatus(), a.getIp(), a.getUserAgent(), a.getDetails(), a.getEventTime());
    }

}
