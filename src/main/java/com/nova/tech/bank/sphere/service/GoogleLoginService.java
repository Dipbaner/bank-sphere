package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.OAuthLoginException;
import com.nova.tech.bank.sphere.repository.UserRepository;
import com.nova.tech.bank.sphere.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleLoginService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final LockoutService lockoutService;
    private final AuditService auditService;

    /** Finds, links or creates the BankSphere user for a Google identity. */
    @Transactional
    public User loginOrRegister(OidcUser oidc) {
        String subject = oidc.getSubject();
        String email = oidc.getEmail() == null ? null : oidc.getEmail().trim().toLowerCase();

        // Never trust an address Google itself has not verified.
        if (subject == null || email == null || !Boolean.TRUE.equals(oidc.getEmailVerified())) {
            auditService.log(null, email, AuditAction.OAUTH_LOGIN_FAILED, AuditStatus.FAILURE,
                    "google email missing or not verified");
            throw new OAuthLoginException("email_not_verified");
        }

        User user = userRepository.findByGoogleSubject(subject).orElse(null);

        if (user == null) {
            User byEmail = userRepository.findByEmail(email).orElse(null);
            user = (byEmail != null)
                    ? linkExisting(byEmail, subject)
                    : createNew(oidc, subject, email);
        } else {
            assertMayLogin(user);
        }

        auditService.log(user.getId(), email, AuditAction.OAUTH_LOGIN_OK, AuditStatus.SUCCESS,
                "google sign-in accepted, pre-auth token will be issued");
        return user;
    }

    private User linkExisting(User user, String subject) {
        // Blocked accounts are never linked or woken up by a Google login.
        if (user.getStatus() != UserStatus.PENDING_EMAIL_VERIFICATION) {
            assertMayLogin(user);
        }

        // This email already belongs to a different Google account: refuse.
        if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(subject)) {
            auditService.log(user.getId(), user.getEmail(), AuditAction.OAUTH_LOGIN_FAILED,
                    AuditStatus.FAILURE, "email is linked to a different google account");
            throw new OAuthLoginException("account_conflict");
        }

        if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
            // Pre-hijack protection: whoever typed this email at registration never proved they own it,
            // so their password must stop working now that the real owner has proved it via Google.
            user.setPasswordHash(null);
            user.setEmailVerified(true);
            user.setStatus(UserStatus.ACTIVE);
            user.setProvider(AuthProvider.GOOGLE);
            tokenRepository.deleteByUserAndType(user, TokenType.EMAIL_VERIFY);
            auditService.log(user.getId(), user.getEmail(), AuditAction.OAUTH_ACCOUNT_LINKED,
                    AuditStatus.SUCCESS, "unverified local registration converted, local password removed");
        } else {
            auditService.log(user.getId(), user.getEmail(), AuditAction.OAUTH_ACCOUNT_LINKED,
                    AuditStatus.SUCCESS, "google account linked to existing verified account");
        }

        user.setGoogleSubject(subject);
        return userRepository.save(user);
    }

    /* ------------------------------------------------------------- create */

    private User createNew(OidcUser oidc, String subject, String email) {
        User created = userRepository.save(User.builder()
                .name(cleanName(oidc.getFullName()))
                .email(email)
                .phone(null)                          // collected later, during KYC / profile
                .passwordHash(null)                   // Google-only account: no local password
                .role(Role.CUSTOMER)                  // never taken from Google
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.GOOGLE)
                .googleSubject(subject)
                .failedAttempts(0)
                .emailVerified(true)
                .build());

        auditService.log(created.getId(), email, AuditAction.OAUTH_ACCOUNT_CREATED, AuditStatus.SUCCESS,
                "customer account created from google sign-in");
        return created;
    }

    /* ------------------------------------------------------------ helpers */

    private void assertMayLogin(User user) {
        if (user.getStatus() == UserStatus.DISABLED || user.getStatus() == UserStatus.LOCKED
                || user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
            auditService.log(user.getId(), user.getEmail(), AuditAction.OAUTH_LOGIN_FAILED,
                    AuditStatus.FAILURE, "account status " + user.getStatus());
            throw new OAuthLoginException("account_blocked");
        }
        if (lockoutService.isLocked(user)) {
            auditService.log(user.getId(), user.getEmail(), AuditAction.OAUTH_LOGIN_FAILED,
                    AuditStatus.FAILURE, "temporarily locked");
            throw new OAuthLoginException("account_locked");
        }
    }

    private static String cleanName(String fullName) {
        String n = fullName == null ? "" : fullName.replaceAll("\\p{Cntrl}", " ").trim();
        if (n.length() < 2) n = "Google User";
        return n.length() > 60 ? n.substring(0, 60) : n;
    }
    }
