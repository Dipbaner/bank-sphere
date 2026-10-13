package com.nova.tech.bank.sphere.repository;

import com.nova.tech.bank.sphere.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AuditLogRepository extends
        JpaRepository<AuditLog, Long> {
    AuditLog save(AuditLog log);
    Page<AuditLog> findAll(Pageable pageable);
}
