package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.AuditAction;
import com.nova.tech.bank.sphere.entity.AuditLog;
import com.nova.tech.bank.sphere.entity.AuditStatus;
import com.nova.tech.bank.sphere.repository.AuditLogRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    /**
     * REQUIRES_NEW: the audit row is committed on its own, so it is kept even if the
     * calling request later throws. If the audit write itself fails, the error propagates
     * and the request fails too. For a bank, "no audit record, no action" is the safer default.
     * NEVER pass passwords, OTPs, tokens or secrets in 'details'.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String identifier, AuditAction action,
                    AuditStatus status, String details) {

        HttpServletRequest req = currentRequest();

        repository.save(AuditLog.builder()
                .userId(userId)
                .identifier(cut(identifier, 120))
                .action(action)
                .status(status)
                .ip(req != null ? cut(req.getRemoteAddr(), 45) : null)
                .userAgent(req != null ? cut(req.getHeader("User-Agent"), 255) : null)
                .details(cut(details, 255))
                .eventTime(LocalDateTime.now())
                .build());
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes s) ? s.getRequest() : null;
    }

    private static String cut(String v, int max) {
        if (v == null) return null;
        String clean = v.replaceAll("[\\r\\n\\t]", " ");   // stops log-injection through fake line breaks
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
