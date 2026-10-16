package com.nova.tech.bank.sphere.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /* ---------------------------------------------------- verify email */

    @Async
    public void sendVerificationEmail(String to, String name, String rawToken) {
        String link = frontendUrl + "/verify-email?token=" + rawToken;
        String body = "Hello " + name + ",\n\n"
                + "Welcome to BankSphere. Please verify your email by opening this link:\n\n"
                + link + "\n\n"
                + "This link expires in 24 hours. If you did not create this account, ignore this email.\n\n"
                + "BankSphere Security Team";
        send(to, "Verify your BankSphere account", body);
    }

    /* ------------------------------------------------------- lockout */

    @Async
    public void sendLockoutEmail(String to, String name, long minutes) {
        String body = "Hello " + name + ",\n\n"
                + "Your BankSphere account was temporarily locked for " + minutes
                + " minutes after several failed attempts.\n\n"
                + "If this was you, wait and try again. If it was not you, please reset your "
                + "password as soon as the lock ends and contact the bank.\n\n"
                + "BankSphere Security Team";
        send(to, "Your BankSphere account was locked", body);
    }

    /* ------------------------------------------------ password reset */

    @Async
    public void sendPasswordResetEmail(String to, String name, String rawToken) {
        String link = frontendUrl + "/reset-password?token=" + rawToken;
        String body = "Hello " + name + ",\n\n"
                + "We received a request to reset your BankSphere password. Open this link to choose a new one:\n\n"
                + link + "\n\n"
                + "The link works once and expires in 15 minutes. If you did not ask for this, ignore this email; "
                + "your password has not changed.\n\n"
                + "BankSphere Security Team";
        send(to, "Reset your BankSphere password", body);
    }

    @Async
    public void sendPasswordChangedEmail(String to, String name) {
        String body = "Hello " + name + ",\n\n"
                + "Your BankSphere password was just changed and you were signed out of all devices.\n\n"
                + "If this was you, no action is needed. If it was NOT you, contact the bank immediately.\n\n"
                + "BankSphere Security Team";
        send(to, "Your BankSphere password was changed", body);
    }

    /* ------------------------------------------------------- helper */

    private void send(String to, String subject, String text) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(text);
            mailSender.send(msg);
        } catch (Exception e) {
            // Never fail the request because the mail server is down. Never log links or tokens.
            log.error("Could not send email to {}: {}", to, e.getMessage());
        }
    }
}