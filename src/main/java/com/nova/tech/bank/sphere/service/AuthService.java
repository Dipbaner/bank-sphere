package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.RegisterRequest;
import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.DuplicateResourceException;
import com.nova.tech.bank.sphere.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.Value;
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

    @org.springframework.beans.factory.annotation.Value("${app.verification.expiry-hours:24}")
    private long verificationExpiryHours;

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

}
