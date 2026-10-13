package com.nova.tech.bank.sphere.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Immutable                                   // Hibernate will never issue UPDATE for this entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_user", columnList = "user_id"),
        @Index(name = "idx_audit_time", columnList = "event_time")
})
@Getter                                      // getters only, no setters
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain number, not a foreign key: the log must survive even if a user is deleted.
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 120)
    private String identifier;               // the email that was typed, useful for unknown accounts

    // columnDefinition keeps this a plain varchar. Hibernate 6 would otherwise create a
    // MySQL ENUM column, and adding new actions in later steps would then fail.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(40)")
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10)")
    private AuditStatus status;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(length = 255)
    private String details;

    @Column(name = "event_time", nullable = false, updatable = false)
    private LocalDateTime eventTime;
}

