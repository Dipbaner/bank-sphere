package com.nova.tech.bank.sphere.repository;

import com.nova.tech.bank.sphere.entity.TokenType;
import com.nova.tech.bank.sphere.entity.User;
import com.nova.tech.bank.sphere.entity.VerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface VerificationTokenRepository extends
        JpaRepository<VerificationToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VerificationToken> findByTokenHashAndType(
            String tokenHash, TokenType type);

    Optional<VerificationToken>
    findFirstByUserAndTypeAndUsedFalseOrderByIdDesc
            (User user, TokenType type);

    void deleteByUserAndType(User user, TokenType type);
}
