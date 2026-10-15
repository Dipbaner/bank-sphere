package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.AuthTokensResponse;
import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.InvalidRefreshTokenException;
import com.nova.tech.bank.sphere.repository.RefreshTokenRepository;
import com.nova.tech.bank.sphere.repository.UserRepository;
import com.nova.tech.bank.sphere.security.JwtService;
import com.nova.tech.bank.sphere.util.RequestInfo;
import com.nova.tech.bank.sphere.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final UserRepository userRepository;
    private final LockoutService lockoutService;
    private final JwtService jwtService;
    private final AuditService auditService;

    @Value("${app.jwt.refresh-days}")
    private long refreshDays;

    @Value("${app.jwt.session-max-days}")
    private long sessionMaxDays;

    private record Created(RefreshToken entity, String raw) {}

    /** Called right after a successful second factor. Returns the RAW refresh token. */
    @Transactional
    public String startSession(User user) {
        LocalDateTime now = LocalDateTime.now();
        repo.deleteExpiredBefore(now.minusDays(1));          // light housekeeping
        return create(user.getId(), UUID.randomUUID().toString(), now, now).raw();
    }

    /**
     * Swaps a valid refresh token for a new access token AND a new refresh token.
     * noRollbackFor: when theft is detected we must COMMIT the revocation even though we throw.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokensResponse rotate(String rawToken) {
        RefreshToken old = repo.findByTokenHashForUpdate(TokenUtil.sha256(rawToken))   // row lock
                .orElseThrow(InvalidRefreshTokenException::new);
        LocalDateTime now = LocalDateTime.now();

        // 1) A token that was already used or revoked is being presented again: likely stolen.
        if (old.isRevoked()) {
            repo.revokeFamily(old.getFamilyId());
            auditService.log(old.getUserId(), null, AuditAction.TOKEN_REUSE_DETECTED, AuditStatus.FAILURE,
                    "revoked refresh token presented, session revoked");
            throw new InvalidRefreshTokenException();
        }

        // 2) Expired, or the whole session is older than the hard limit.
        if (old.getExpiresAt().isBefore(now)
                || old.getSessionStartedAt().plusDays(sessionMaxDays).isBefore(now)) {
            throw new InvalidRefreshTokenException();
        }

        // 3) The account must still be allowed to sign in (disabled, locked, deleted...).
        User user = userRepository.findById(old.getUserId()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || lockoutService.isLocked(user)) {
            repo.revokeFamily(old.getFamilyId());
            throw new InvalidRefreshTokenException();
        }

        // 4) Rotate: new token in the same family, old one retired and linked to it.
        Created next = create(user.getId(), old.getFamilyId(), old.getSessionStartedAt(), now);
        old.setRevoked(true);
        old.setReplacedBy(next.entity().getId());

        return new AuthTokensResponse(jwtService.generateAccessToken(user), next.raw(),
                user.getRole().name(), jwtService.accessSeconds());
    }

    /** Ends one session. Silent if the token is unknown, so it can be called safely twice. */
    @Transactional
    public void logout(Long userId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repo.findByTokenHashForUpdate(TokenUtil.sha256(rawToken))
                .filter(t -> t.getUserId().equals(userId))          // only your own sessions
                .ifPresent(t -> repo.revokeFamily(t.getFamilyId()));
    }

    /** Ends every session of the user. Returns how many refresh tokens were revoked. */
    @Transactional
    public int logoutAll(Long userId) {
        return repo.revokeAllForUser(userId);
    }

    private Created create(Long userId, String familyId, LocalDateTime sessionStart, LocalDateTime now) {
        String raw = TokenUtil.generateRawToken();
        LocalDateTime expires = now.plusDays(refreshDays);
        LocalDateTime hardLimit = sessionStart.plusDays(sessionMaxDays);
        if (expires.isAfter(hardLimit)) expires = hardLimit;

        RefreshToken saved = repo.save(RefreshToken.builder()
                .userId(userId)
                .familyId(familyId)
                .tokenHash(TokenUtil.sha256(raw))        // only the hash is stored
                .sessionStartedAt(sessionStart)
                .expiresAt(expires)
                .revoked(false)
                .ip(RequestInfo.ip())
                .userAgent(RequestInfo.userAgent())
                .createdAt(now)
                .build());
        return new Created(saved, raw);
    }
}
