package com.nova.tech.bank.sphere.service;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import static jakarta.mail.Transport.send;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.base-url}")
    private String baseUrl;

    @Async
    public void sendVerificationEmail(String to, String name, String rawToken) {
        String link = baseUrl + "/api/auth/verify-email?token=" + rawToken;
        String body = "Hello " + name + ",\n\n"
                + "Welcome to BankSphere. Please verify your email by opening this link:\n\n"
                + link + "\n\n"
                + "This link expires in 24 hours. If you did not create this account, ignore this email.\n\n"
                + "BankSphere Security Team";
        send(to, "Verify your BankSphere account", body);
    }

    private void send(String to, String subject, String text) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(text);
            mailSender.send(msg);
        } catch (Exception e) {
            // Never fail registration because the mail server is down.
            log.error("Could not send email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendLockoutEmail(String to, String name, long minutes) {
        String body = "Hello " + name + ",\n\n"
                + "Your BankSphere account was temporarily locked for " + minutes
                + " minutes after several failed login attempts.\n\n"
                + "If this was you, wait and try again. If it was not you, please reset your "
                + "password as soon as the lock ends and contact the bank.\n\n"
                + "BankSphere Security Team";
        send(to, "Your BankSphere account was locked", body);
    }
}
