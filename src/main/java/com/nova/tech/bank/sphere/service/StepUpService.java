package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.StepUpRequest;
import com.nova.tech.bank.sphere.dto.StepUpResponse;
import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.*;
import com.nova.tech.bank.sphere.repository.StepUpTokenRepository;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StepUpService {

    public static final String HEADER = "X-Step-Up-Token";

    private final StepUpTokenRepository repo;
    private final OtpService otpService;
    private final AuditService auditService;

    @Value("${app.stepup.token-seconds}")
    private long tokenSeconds;

    @Transactional(noRollbackFor = {InvalidOtpException.class, AccountStateException.class})
    public StepUpResponse issue(AuthenticatedUser me, StepUpRequest req) {
        otpService.confirmCode(me.id(), req.getOtp());

        LocalDateTime now = LocalDateTime.now();
        repo.deleteExpiredBefore(now.minusDays(1));

        String raw = TokenUtil.generateRawToken();
        repo.save(StepUpToken.builder()
                .userId(me.id())
                .action(req.getAction())
                .context(normalize(req.getContext()))
                .tokenHash(TokenUtil.sha256(raw))
                .expiresAt(now.plusSeconds(tokenSeconds))
                .used(false)
                .createdAt(now)
                .build());

        auditService.log(me.id(), me.email(), AuditAction.STEP_UP_ISSUED, AuditStatus.SUCCESS,
                req.getAction().name());
        return new StepUpResponse(raw, req.getAction(), tokenSeconds);
    }

    @Transactional
    public void require(Long userId, StepUpAction action, String rawToken, String context) {
        String ctx = normalize(context);
        boolean hasToken = rawToken != null && !rawToken.isBlank();
        boolean ok = hasToken
                && repo.consume(TokenUtil.sha256(rawToken), userId, action, ctx, LocalDateTime.now()) == 1;

        if (!ok) {
            if (hasToken) {
                auditService.log(userId, null, AuditAction.STEP_UP_REJECTED, AuditStatus.FAILURE,
                        action.name() + " (wrong, expired, used or mismatched token)");
            }
            throw new StepUpRequiredException(action, ctx);
        }
        auditService.log(userId, null, AuditAction.STEP_UP_USED, AuditStatus.SUCCESS, action.name());
    }

    private static String normalize(String context) {
        return context == null ? "" : context.trim();
    }
}