package com.nova.tech.bank.sphere.repository;

import com.nova.tech.bank.sphere.entity.OtpSecret;
import com.nova.tech.bank.sphere.entity.OtpStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpSecretRepository extends JpaRepository<OtpSecret, Long> {

    boolean existsByUserIdAndStatus(Long userId, OtpStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OtpSecret o where o.userId = :userId")
    Optional<OtpSecret> findByUserIdForUpdate(@Param("userId") Long userId);
}
