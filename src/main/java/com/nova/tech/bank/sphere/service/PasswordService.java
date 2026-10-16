package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.ChangePasswordRequest;
import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.*;
import com.nova.tech.bank.sphere.repository.PasswordHistoryRepository;
import com.nova.tech.bank.sphere.repository.UserRepository;
import com.nova.tech.bank.sphere.security.AccessTokenBlacklist;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService tokenService;
    private final MailService mailService;
    private final PasswordHistoryRepository historyRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final OtpService otpService;
    private final LockoutService lockoutService;
    private final AccessTokenBlacklist accessTokenBlacklist;

    @Value("${app.password-reset.expiry-minutes}")
    private long resetMinutes;

    @Value("${app.password-reset.cooldown-seconds}")
    private long cooldownSeconds;

    @Value("${app.password.history-count}")
    private int historyCount;

    @Value("${app.security.lock-minutes}")
    private long lockMinutes;

    /* ================================================== forgot password */

    /** Always finishes silently, so the caller can never tell if the email exists. */
    @Transactional
    public void requestReset(String emailRaw) {
        String email = emailRaw.trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getPasswordHash() != null)
                .orElse(null);

        if (user == null) {
            auditService.log(null, email, AuditAction.PASSWORD_RESET_REQUESTED, AuditStatus.FAILURE,
                    "unknown or ineligible account");
            return;
        }

        Duration validity = Duration.ofMinutes(resetMinutes);
        if (tokenService.issuedRecently(user, TokenType.PASSWORD_RESET, validity,
                Duration.ofSeconds(cooldownSeconds))) {
            auditService.log(user.getId(), email, AuditAction.PASSWORD_RESET_REQUESTED, AuditStatus.FAILURE,
                    "cooldown, no email sent");
            return;
        }
        String raw = tokenService.createToken(user, TokenType.PASSWORD_RESET, validity);
        mailService.sendPasswordResetEmail(user.getEmail(), user.getName(), raw);
        auditService.log(user.getId(), email, AuditAction.PASSWORD_RESET_REQUESTED, AuditStatus.SUCCESS,
                "reset email sent");
    }

    /* =================================================== reset password */

    /**
     * One transaction: if anything below throws (weak password, reused password...)
     * the token is NOT burned, so the user can retry with the same link.
     */

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = tokenService.consume(rawToken, TokenType.PASSWORD_RESET);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidTokenException("Invalid or expired link");
        }

        validateNewPassword(user, newPassword);
        storeNewPassword(user, newPassword);
        user.setFailedAttempts(0);          // the lock timer, if any, is deliberately left alone
        userRepository.save(user);

        refreshTokenService.logoutAll(user.getId());
        auditService.log(user.getId(), user.getEmail(), AuditAction.PASSWORD_RESET, AuditStatus.SUCCESS,
                "password reset by email link, all sessions revoked");
        mailService.sendPasswordChangedEmail(user.getEmail(), user.getName());
    }

    /* ================================================== change password */

    /**
     * noRollbackFor: the failure counters (wrong password, wrong OTP) must be saved
     * even though we throw, otherwise this endpoint could be used for unlimited guessing.
     */
    @Transactional(noRollbackFor = {PasswordException.class, InvalidOtpException.class,
            AccountStateException.class})
    public void changePassword(AuthenticatedUser me, ChangePasswordRequest req) {
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid session"));

        if (lockoutService.isLocked(user)) {
            throw new AccountStateException("ACCOUNT_LOCKED",
                    "Account is locked. Try again in " + lockoutService.minutesRemaining(user) + " minute(s)");
        }
        if (user.getPasswordHash() == null) {
            throw new PasswordException("NO_LOCAL_PASSWORD",
                    "This account signs in with Google and has no password to change");
        }

        // 1) Current password. A wrong guess counts exactly like a failed login.
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            boolean locked = lockoutService.recordFailedLogin(user.getId());
            auditService.log(user.getId(), user.getEmail(), AuditAction.PASSWORD_CHANGED,
                    AuditStatus.FAILURE, "wrong current password");
            if (locked) {
                auditService.log(user.getId(), user.getEmail(), AuditAction.ACCOUNT_LOCKED,
                        AuditStatus.FAILURE, "too many wrong current passwords");
                mailService.sendLockoutEmail(user.getEmail(), user.getName(), lockMinutes);
            }
            throw new PasswordException("WRONG_CURRENT_PASSWORD", "Current password is incorrect");
        }

        // 2) New password rules BEFORE the OTP, so a typo does not burn a code.
        validateNewPassword(user, req.getNewPassword());

        // 3) Fresh OTP.
        otpService.confirmCode(user.getId(), req.getOtp());

        // 4) Apply, then end every session including this one.
        storeNewPassword(user, req.getNewPassword());
        user.setFailedAttempts(0);
        userRepository.save(user);

        refreshTokenService.logoutAll(user.getId());
        accessTokenBlacklist.revoke(me.jti(), me.expiresAt());

        auditService.log(user.getId(), user.getEmail(), AuditAction.PASSWORD_CHANGED, AuditStatus.SUCCESS,
                "password changed, all sessions revoked");
        mailService.sendPasswordChangedEmail(user.getEmail(), user.getName());
    }

    /* ======================================================== helpers */

    private void validateNewPassword(User user, String newPassword) {
        String email = user.getEmail().toLowerCase();
        String local = email.substring(0, email.indexOf('@'));
        String lower = newPassword.toLowerCase();
        if (lower.equals(email) || (local.length() >= 4 && lower.contains(local))) {
            throw new PasswordException("WEAK_PASSWORD", "Password must not contain your email name");
        }

        // Current password plus the previous N.
        List<String> recent = new ArrayList<>();
        if (user.getPasswordHash() != null) recent.add(user.getPasswordHash());
        historyRepository.findByUserIdOrderByIdDesc(user.getId(), PageRequest.of(0, historyCount))
                .forEach(h -> recent.add(h.getPasswordHash()));

        for (String hash : recent) {
            if (passwordEncoder.matches(newPassword, hash)) {
                throw new PasswordException("PASSWORD_REUSED",
                        "You cannot reuse your current or recent passwords");
            }
        }
    }

    private void storeNewPassword(User user, String newPassword) {
        if (user.getPasswordHash() != null) {
            historyRepository.save(PasswordHistory.builder()
                    .userId(user.getId())
                    .passwordHash(user.getPasswordHash())
                    .changedAt(LocalDateTime.now())
                    .build());
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }
}
