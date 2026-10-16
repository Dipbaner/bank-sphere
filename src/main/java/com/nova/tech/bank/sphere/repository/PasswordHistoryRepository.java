package com.nova.tech.bank.sphere.repository;

import com.nova.tech.bank.sphere.entity.PasswordHistory;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

@Repository
public interface PasswordHistoryRepository extends
    JpaRepository<PasswordHistory, Long>{

    List<PasswordHistory> findByUserIdOrderByIdDesc(
            Long userId, Pageable pageable);
}
