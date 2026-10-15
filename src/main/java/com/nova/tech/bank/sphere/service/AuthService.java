package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.LoginRequest;
import com.nova.tech.bank.sphere.dto.LoginResponse;
import com.nova.tech.bank.sphere.dto.RegisterRequest;
import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.AccountStateException;
import com.nova.tech.bank.sphere.exception.AuthenticationFailedException;
import com.nova.tech.bank.sphere.exception.DuplicateResourceException;
import com.nova.tech.bank.sphere.repository.UserRepository;
import com.nova.tech.bank.sphere.security.JwtService;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService tokenService;
    private final MailService mailService;
    private final OtpService otpService;
    private final LockoutService lockoutService;
    private final AuditService auditService;

    @org.springframework.beans.factory.annotation.Value("${app.verification.expiry-hours:24}")
    private long verificationExpiryHours;

    private final JwtService jwtService;
    private String dummyHash;
    // not final, so Lombok leaves it out of the constructor

    @Value("${app.security.lock-minutes}")
    private long lockMinutes;

    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public void register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email))
            throw new DuplicateResourceException("Email is already registered");
        if (userRepository.existsByPhone(req.getPhone()))
            throw new DuplicateResourceException("Phone number is already registered");

        User user = User.builder()
                .name(req.getName().trim())
                .email(email)
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(Role.CUSTOMER)                        // never taken from the request
                .status(UserStatus.PENDING_EMAIL_VERIFICATION)
                .provider(AuthProvider.LOCAL)
                .failedAttempts(0)
                .emailVerified(false)
                .build();

        userRepository.save(user);

        String raw = tokenService.createToken(
                user, TokenType.EMAIL_VERIFY, Duration.ofHours(verificationExpiryHours));
        mailService.sendVerificationEmail(user.getEmail(), user.getName(), raw);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        User user = tokenService.consume(rawToken, TokenType.EMAIL_VERIFY);
        user.setEmailVerified(true);
        if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);
    }

    /** Always silent: never reveals whether the email exists. */
    @Transactional
    public void resendVerification(String emailRaw) {
        userRepository.findByEmail(emailRaw.trim().toLowerCase())
                .filter(u -> u.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION)
                .ifPresent(u -> {
                    String raw = tokenService.createToken(
                            u, TokenType.EMAIL_VERIFY, Duration.ofHours(verificationExpiryHours));
                    mailService.sendVerificationEmail(u.getEmail(), u.getName(), raw);
                });
    }

    public LoginResponse login(LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);

        // Always run one BCrypt check, even for unknown emails (same response time).
        String hashToCheck = (user != null && user.getPasswordHash() != null)
                ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(req.getPassword(), hashToCheck);

        // 1) Wrong password or unknown account: generic 401, count the failure.
        if (user == null || !passwordOk) {
            if (user == null) {
                auditService.log(null, email, AuditAction.LOGIN_FAILED, AuditStatus.FAILURE,
                        "unknown account");
            } else {
                handleFailedPassword(user, email);
            }
            throw new AuthenticationFailedException("Invalid email or password");
        }

        // 2) Correct password, but the account is temporarily locked.
        if (lockoutService.isLocked(user)) {
            auditService.log(user.getId(), email, AuditAction.LOGIN_BLOCKED, AuditStatus.FAILURE,
                    "temporarily locked");
            throw new AccountStateException("ACCOUNT_LOCKED",
                    "Account is locked. Try again in " + lockoutService.minutesRemaining(user) + " minute(s)");
        }

        // 3) Correct password, but the account state does not allow login.
        switch (user.getStatus()) {
            case PENDING_EMAIL_VERIFICATION -> {
                auditService.log(user.getId(), email, AuditAction.LOGIN_BLOCKED, AuditStatus.FAILURE,
                        "email not verified");
                throw new AccountStateException("EMAIL_NOT_VERIFIED", "Please verify your email first");
            }
            case LOCKED -> {
                auditService.log(user.getId(), email, AuditAction.LOGIN_BLOCKED, AuditStatus.FAILURE,
                        "locked by staff");
                throw new AccountStateException("ACCOUNT_LOCKED", "Account is locked. Please contact the bank");
            }
            case DISABLED -> {
                auditService.log(user.getId(), email, AuditAction.LOGIN_BLOCKED, AuditStatus.FAILURE,
                        "account disabled");
                throw new AccountStateException("ACCOUNT_DISABLED", "Account is disabled. Contact the bank");
            }
            default -> { }
        }

        // 4) Success of step 1.
        lockoutService.resetFailures(user.getId());
        auditService.log(user.getId(), email, AuditAction.LOGIN_PASSWORD_OK, AuditStatus.SUCCESS,
                "pre-auth token issued");

        return new LoginResponse("OTP_REQUIRED",
                jwtService.generatePreAuthToken(user),
                jwtService.preAuthSeconds(),
                !otpService.isEnabled(user.getId()));   // true => the app must show the enrollment screen
    }

    private void handleFailedPassword(User user, String email) {
        // While locked, further wrong guesses are logged but do not extend the lock.
        if (lockoutService.isLocked(user)) {
            auditService.log(user.getId(), email, AuditAction.LOGIN_FAILED, AuditStatus.FAILURE,
                    "wrong password while locked");
            return;
        }

        boolean justLocked = lockoutService.recordFailedLogin(user.getId());
        auditService.log(user.getId(), email, AuditAction.LOGIN_FAILED, AuditStatus.FAILURE,
                "wrong password");

        if (justLocked) {
            auditService.log(user.getId(), email, AuditAction.ACCOUNT_LOCKED, AuditStatus.FAILURE,
                    "too many failed attempts");
            // 'user' was loaded before the lock; the duration comes from configuration.
            mailService.sendLockoutEmail(user.getEmail(), user.getName(), lockMinutes);
        }
    }

}
