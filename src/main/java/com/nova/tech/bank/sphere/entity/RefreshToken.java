package com.nova.tech.bank.sphere.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_rt_user", columnList = "user_id"),
        @Index(name = "idx_rt_family", columnList = "family_id"),
        @Index(name = "idx_rt_expires", columnList = "expires_at")
})
@Getter @Setter @NoArgsConstructor
@AllArgsConstructor @Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** All tokens produced by rotating one login share a family id. */
    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** When the original login happened. Used for the 30-day hard limit. */
    @Column(name = "session_started_at", nullable = false)
    private LocalDateTime sessionStartedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "replaced_by")
    private Long replacedBy;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
