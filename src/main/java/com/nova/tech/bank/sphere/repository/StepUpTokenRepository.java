package com.nova.tech.bank.sphere.repository;

import com.nova.tech.bank.sphere.entity.StepUpAction;
import com.nova.tech.bank.sphere.entity.StepUpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface StepUpTokenRepository extends JpaRepository<StepUpToken, Long> {

    /** Atomic: returns 1 only when this exact user, action and context match an unused, unexpired token. */
    @Modifying
    @Query("update StepUpToken t set t.used = true " +
            "where t.tokenHash = :hash and t.userId = :uid and t.action = :action " +
            "and t.context = :ctx and t.used = false and t.expiresAt > :now")
    int consume(@Param("hash") String hash, @Param("uid") Long userId,
                @Param("action") StepUpAction action, @Param("ctx") String context,
                @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from StepUpToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}