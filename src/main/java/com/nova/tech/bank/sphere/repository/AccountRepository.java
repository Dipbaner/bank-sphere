package com.nova.tech.bank.sphere.repository;

import com.nova.tech.bank.sphere.entity.Account;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /** SELECT ... FOR UPDATE. Ownership is part of the query: foreign and unknown accounts look the same. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select a from Account a where a.accountNumber = :no and a.userId = :userId")
    Optional<Account> lockByAccountNumberAndUserId(@Param("no") String accountNumber,
                                                   @Param("userId") Long userId);

    List<Account> findByUserIdOrderByIdAsc(Long userId);
}
