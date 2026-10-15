package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.AuthTokensResponse;
import com.nova.tech.bank.sphere.dto.BackupLoginResponse;
import com.nova.tech.bank.sphere.dto.EnrollConfirmResponse;
import com.nova.tech.bank.sphere.dto.EnrollResponse;
import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.AccountStateException;
import com.nova.tech.bank.sphere.exception.AuthenticationFailedException;
import com.nova.tech.bank.sphere.exception.InvalidOtpException;
import com.nova.tech.bank.sphere.exception.OtpException;
import com.nova.tech.bank.sphere.repository.BackupCodeRepository;
import com.nova.tech.bank.sphere.repository.OtpSecretRepository;
import com.nova.tech.bank.sphere.repository.UserRepository;
import com.nova.tech.bank.sphere.security.JwtService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final String BACKUP_ALPHABET
            = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I, O, 0, 1
    private static final int BACKUP_CODE_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpSecretRepository otpRepo;
    private final BackupCodeRepository backupRepo;
    private final UserRepository userRepository;
    private final TotpService totpService;
    private final CryptoService crypto;
    private final LockoutService lockoutService;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final MailService mailService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.totp.issuer}")
    private String issuer;

    @Value("${app.otp.max-attempts}")
    private int maxAttempts;

    @Value("${app.otp.backup-code-count}")
    private int backupCodeCount;

    @Value("${app.security.lock-minutes}")
    private long lockMinutes;

    public boolean isEnabled(Long userId) {
        return otpRepo.existsByUserIdAndStatus(userId, OtpStatus.ENABLED);
    }

    @Transactional
    public EnrollResponse startEnrollment(Long userId) {
        User user = loadUsableUser(userId);

        OtpSecret otp = otpRepo.findByUserIdForUpdate(userId).orElse(null);
        if (otp != null && otp.getStatus() == OtpStatus.ENABLED) {
            // A password alone must never be able to replace the second factor.
            throw new OtpException("OTP_ALREADY_ENABLED",
                    "An authenticator is already set up for this account");
        }

        String secret = totpService.generateSecret();
        if (otp == null) otp = OtpSecret.builder().userId(userId).build();
        otp.setSecretEncrypted(crypto.encrypt(secret, aad(userId)));
        otp.setStatus(OtpStatus.PENDING);
        otp.setLastUsedStep(0);
        otp.setOtpFailures(0);
        otpRepo.save(otp);

        return new EnrollResponse(secret,
                totpService.buildOtpAuthUri(issuer, user.getEmail(), secret),
                issuer, user.getEmail());
    }

    @Transactional(noRollbackFor = {InvalidOtpException.class, AccountStateException.class})
    public EnrollConfirmResponse confirmEnrollment(Long userId, String code) {
        User user = loadUsableUser(userId);

        OtpSecret otp = otpRepo.findByUserIdForUpdate(userId)
                .filter(o -> o.getStatus() == OtpStatus.PENDING)
                .orElseThrow(() -> new OtpException("NO_PENDING_ENROLLMENT",
                        "Start the authenticator setup first"));

        String secret = crypto.decrypt(otp.getSecretEncrypted(), aad(userId));
        OptionalLong step = totpService.verify(secret, code, 0);
        if (step.isEmpty()) throw failOtp(user, otp, "wrong code during enrollment");

        otp.setStatus(OtpStatus.ENABLED);
        otp.setLastUsedStep(step.getAsLong());
        otp.setOtpFailures(0);
        otp.setEnrolledAt(LocalDateTime.now());
        otpRepo.save(otp);

        List<String> backupCodes = regenerateBackupCodes(userId);

        auditService.log(userId, user.getEmail(), AuditAction.OTP_ENROLLED, AuditStatus.SUCCESS,
                "authenticator enrolled");
        auditService.log(userId, user.getEmail(), AuditAction.LOGIN_SUCCESS, AuditStatus.SUCCESS,
                "enrollment completed login");

        // confirmEnrollment
        return new EnrollConfirmResponse(backupCodes,
                jwtService.generateAccessToken(user),
                refreshTokenService.startSession(user),
                user.getRole().name(), jwtService.accessSeconds());
    }

    @Transactional(noRollbackFor = {InvalidOtpException.class, AccountStateException.class})
    public AuthTokensResponse verifyLogin(Long userId, String code) {
        User user = loadUsableUser(userId);
        OtpSecret otp = loadEnabled(userId);

        String secret = crypto.decrypt(otp.getSecretEncrypted(), aad(userId));
        OptionalLong step = totpService.verify(secret, code, otp.getLastUsedStep());
        if (step.isEmpty()) throw failOtp(user, otp, "wrong, expired or reused code");

        otp.setLastUsedStep(step.getAsLong());     // remember it: this code can never be used again
        otp.setOtpFailures(0);
        otpRepo.save(otp);

        auditService.log(userId, user.getEmail(), AuditAction.LOGIN_SUCCESS, AuditStatus.SUCCESS,
                "otp verified");

        // verifyLogin
        return new AuthTokensResponse(jwtService.generateAccessToken(user),
                refreshTokenService.startSession(user),
                user.getRole().name(), jwtService.accessSeconds());
    }

    @Transactional(noRollbackFor = {InvalidOtpException.class, AccountStateException.class})
    public BackupLoginResponse verifyBackupCode(Long userId, String rawCode) {
        User user = loadUsableUser(userId);
        OtpSecret otp = loadEnabled(userId);

        String normalized = rawCode.toUpperCase().replaceAll("[\\s-]", "");
        boolean wellFormed = normalized.matches("[A-HJ-NP-Z2-9]{" + BACKUP_CODE_LENGTH + "}");

        boolean accepted = wellFormed
                && backupRepo.consume(userId, hashBackup(userId, normalized), LocalDateTime.now()) == 1;
        if (!accepted) throw failOtp(user, otp, "wrong or used backup code");

        otp.setOtpFailures(0);
        otpRepo.save(otp);

        auditService.log(userId, user.getEmail(), AuditAction.BACKUP_CODE_USED, AuditStatus.SUCCESS,
                "backup code used");
        auditService.log(userId, user.getEmail(), AuditAction.LOGIN_SUCCESS, AuditStatus.SUCCESS,
                "backup code login");

// verifyBackupCode
        return new BackupLoginResponse(jwtService.generateAccessToken(user),
                refreshTokenService.startSession(user),
                user.getRole().name(), jwtService.accessSeconds(),
                (int) backupRepo.countByUserIdAndUsedFalse(userId));
    }

    /** Counts the failure, locks the account at the limit, and returns the exception to throw. */
    private InvalidOtpException failOtp(User user, OtpSecret otp, String reason) {
        otp.setOtpFailures(otp.getOtpFailures() + 1);
        boolean lock = otp.getOtpFailures() >= maxAttempts;
        if (lock) {
            otp.setOtpFailures(0);                 // fresh allowance once the lock ends
            lockoutService.lockNow(user.getId());
        }
        otpRepo.save(otp);

        auditService.log(user.getId(), user.getEmail(), AuditAction.OTP_FAILED, AuditStatus.FAILURE, reason);
        if (lock) {
            auditService.log(user.getId(), user.getEmail(), AuditAction.ACCOUNT_LOCKED,
                    AuditStatus.FAILURE, "too many wrong OTP attempts");
            mailService.sendLockoutEmail(user.getEmail(), user.getName(), lockMinutes);
        }
        return new InvalidOtpException();
    }

    /** The pre-auth token may outlive a lock, so the account state is re-checked on every call. */
    private User loadUsableUser(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid session"));

        if (lockoutService.isLocked(u)) {
            throw new AccountStateException("ACCOUNT_LOCKED",
                    "Account is locked. Try again in " + lockoutService.minutesRemaining(u) + " minute(s)");
        }
        switch (u.getStatus()) {
            case PENDING_EMAIL_VERIFICATION ->
                    throw new AccountStateException("EMAIL_NOT_VERIFIED", "Please verify your email first");
            case LOCKED ->
                    throw new AccountStateException("ACCOUNT_LOCKED", "Account is locked. Please contact the bank");
            case DISABLED ->
                    throw new AccountStateException("ACCOUNT_DISABLED", "Account is disabled. Contact the bank");
            default -> { }
        }
        return u;
    }

    private OtpSecret loadEnabled(Long userId) {
        return otpRepo.findByUserIdForUpdate(userId)
                .filter(o -> o.getStatus() == OtpStatus.ENABLED)
                .orElseThrow(() -> new OtpException("OTP_NOT_ENROLLED",
                        "Set up your authenticator first"));
    }

    private List<String> regenerateBackupCodes(Long userId) {
        backupRepo.deleteAllForUser(userId);
        List<String> display = new ArrayList<>();
        for (int i = 0; i < backupCodeCount; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                sb.append(BACKUP_ALPHABET.charAt(RANDOM.nextInt(BACKUP_ALPHABET.length())));
            }
            String raw = sb.toString();
            backupRepo.save(BackupCode.builder()
                    .userId(userId).codeHash(hashBackup(userId, raw)).used(false).build());
            display.add(raw.substring(0, 5) + "-" + raw.substring(5));
        }
        return display;
    }

    private String hashBackup(Long userId, String raw) {
        return crypto.hmac("backup:" + userId + ":" + raw);
    }

    private static String aad(Long userId) {
        return "otp-secret:" + userId;
    }
}
