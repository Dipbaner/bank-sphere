package com.nova.tech.bank.sphere.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_secrets")
@Getter @Setter @NoArgsConstructor
@AllArgsConstructor @Builder
public class OtpSecret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "secret_encrypted", nullable = false, length = 255)
    private String secretEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10)")
    private OtpStatus status;

    @Column(name = "last_used_step", nullable = false)
    private long lastUsedStep;

    @Column(name = "otp_failures", nullable = false)
    private int otpFailures;

    @Column(name = "enrolled_at")
    private LocalDateTime enrolledAt;
}
