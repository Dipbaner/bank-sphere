package com.nova.tech.bank.sphere.repository;

import com.nova.tech.bank.sphere.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, Long>, JpaSpecificationExecutor<BankTransaction> {

    Optional<BankTransaction> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** Everything that LEFT this account since 'start' (withdrawals and outgoing transfers). Null if none. */
    @Query("select sum(t.amount) from BankTransaction t " +
            "where t.fromAccountId = :accountId and t.createdAt >= :start " +
            "and t.status = com.nova.tech.bank.sphere.entity.TransactionStatus.SUCCESS")
    BigDecimal sumDebitsSince(@Param("accountId") Long accountId, @Param("start") LocalDateTime start);

    default BigDecimal debitedSince(Long accountId, LocalDateTime start) {
        BigDecimal sum = sumDebitsSince(accountId, start);
        return sum == null ? BigDecimal.ZERO : sum;
    }
}