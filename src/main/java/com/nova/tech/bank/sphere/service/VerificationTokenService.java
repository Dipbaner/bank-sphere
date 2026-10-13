package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.TokenType;
import com.nova.tech.bank.sphere.entity.User;
import com.nova.tech.bank.sphere.entity.VerificationToken;
import com.nova.tech.bank.sphere.exception.InvalidTokenException;
import com.nova.tech.bank.sphere.repository.VerificationTokenRepository;
import com.nova.tech.bank.sphere.util.TokenUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    private final VerificationTokenRepository repository;

    /** Creates a new token, removes older ones of the same type, returns the RAW token. */
    @Transactional
    public String createToken(User user, TokenType type, Duration validity) {
        repository.deleteByUserAndType(user, type);
        repository.flush();

        String raw = TokenUtil.generateRawToken();
        repository.save(VerificationToken.builder()
                .user(user)
                .tokenHash(TokenUtil.sha256(raw))
                .type(type)
                .expiresAt(LocalDateTime.now().plus(validity))
                .used(false)
                .build());
        return raw;
    }

    /** Validates and burns the token. Returns the user it belongs to. */
    @Transactional
    public User consume(String rawToken, TokenType type) {
        VerificationToken t = repository
                .findByTokenHashAndType(TokenUtil.sha256(rawToken), type)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired link"));

        if (t.isUsed() || t.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new InvalidTokenException("Invalid or expired link");

        t.setUsed(true);
        return t.getUser();
    }
}
