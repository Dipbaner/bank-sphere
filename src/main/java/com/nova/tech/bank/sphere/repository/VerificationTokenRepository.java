package com.nova.tech.bank.sphere.repository;

import com.nova.tech.bank.sphere.entity.TokenType;
import com.nova.tech.bank.sphere.entity.User;
import com.nova.tech.bank.sphere.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationTokenRepository extends
        JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByTokenHashAndType(
            String tokenHash, TokenType type);

    void deleteByUserAndType(User user, TokenType type);
}
