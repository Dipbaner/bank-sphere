package com.nova.tech.bank.sphere.repository;

import com.nova.tech.bank.sphere.entity.BackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface BackupCodeRepository extends
        JpaRepository<BackupCode, Long> {


    long countByUserIdAndUsedFalse(Long userId);

    @Modifying
    @Query("delete from BackupCode b where b.userId = :uid")
    void deleteAllForUser(@Param("uid") Long uid);

    /** Atomic: returns 1 only for the single request that actually burns the code. */
    @Modifying
    @Query("update BackupCode b set b.used = true, b.usedAt = :now " +
            "where b.userId = :uid and b.codeHash = :hash " +
            "and b.used = false")
    int consume(@Param("uid") Long uid, @Param("hash")
    String hash, @Param("now") LocalDateTime now);

}
