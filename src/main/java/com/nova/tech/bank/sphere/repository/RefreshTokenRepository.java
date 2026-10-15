package com.nova.tech.bank.sphere.repository;


import com.nova.tech.bank.sphere.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends
    JpaRepository<RefreshToken, Long>{

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.familyId = :fid and r.revoked = false")
    int revokeFamily(@Param("fid") String familyId);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.userId = :uid and r.revoked = false")
    int revokeAllForUser(@Param("uid") Long userId);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
