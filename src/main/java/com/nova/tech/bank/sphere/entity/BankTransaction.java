package com.nova.tech.bank.sphere.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Immutable                         // Hibernate will never UPDATE a ledger row
@Table(name = "transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_txn_user_idem", columnNames = {"user_id", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_txn_from", columnList = "from_account_id, created_at"),
                @Index(name = "idx_txn_to", columnList = "to_account_id, created_at")
        })
@Getter                            // getters only, no setters
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", nullable = false, unique = true, length = 30)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(15)")
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10)")
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Money leaves this account (null for a deposit). */
    @Column(name = "from_account_id")
    private Long fromAccountId;

    /** Money arrives in this account (null for a withdrawal). */
    @Column(name = "to_account_id")
    private Long toAccountId;

    // Account numbers are copied in, so the record stays readable even if an account changes later.
    @Column(name = "from_account_number", length = 20)
    private String fromAccountNumber;

    @Column(name = "to_account_number", length = 20)
    private String toAccountNumber;

    // Balance of each account right AFTER this transaction (statements and running balances need it).
    @Column(name = "from_balance_after", precision = 19, scale = 2)
    private BigDecimal fromBalanceAfter;

    @Column(name = "to_balance_after", precision = 19, scale = 2)
    private BigDecimal toBalanceAfter;

    @Column(length = 140)
    private String description;

    /** The customer who started the transaction. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    /** Hash of (type, accounts, amount): detects a key being reused for a different payment. */
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}