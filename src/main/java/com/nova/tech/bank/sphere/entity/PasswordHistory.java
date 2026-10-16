package com.nova.tech.bank.sphere.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_history",
        indexes = @Index(name = "idx_pwh_user",
                columnList = "user_id"))
@Getter @Setter @NoArgsConstructor
@AllArgsConstructor @Builder
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
