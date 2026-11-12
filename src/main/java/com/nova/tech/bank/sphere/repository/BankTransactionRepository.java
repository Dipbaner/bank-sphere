package com.nova.tech.bank.sphere.repository;

import com.nova.tech.bank.sphere.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, Long>, JpaSpecificationExecutor<BankTransaction> {

    Optional<BankTransaction> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** Money that left the account since the start of the day (withdrawals and outgoing transfers). */
    @Query("select coalesce(sum(t.amount), 0) from BankTransaction t "
            + "where t.fromAccountId = :accountId and t.createdAt >= :since")
    BigDecimal sumDebitsSince(@Param("accountId") Long accountId, @Param("since") Instant since);
}
