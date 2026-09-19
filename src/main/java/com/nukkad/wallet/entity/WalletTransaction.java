package com.nukkad.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * Append-only ledger row. No service method ever updates or deletes a row here — a correction is
 * always a new, separate compensating transaction (see WalletService), never an edit of this one.
 * {@code amountMinorUnits} is always positive; direction is carried by {@code type}.
 */
@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "wallet_id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private String walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private WalletTransactionType type;

    @Column(name = "amount_minor_units", nullable = false, updatable = false)
    private long amountMinorUnits;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WalletTransactionStatus status = WalletTransactionStatus.COMPLETED;

    /** e.g. "ADMIN_ADJUSTMENT", "PAYMENT" — generic link to whatever caused this entry, without
     *  an FK coupling the ledger to any specific business table. */
    @Column(name = "reference_type", length = 40, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", length = 64, updatable = false)
    private String referenceId;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(length = 255, updatable = false)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at", updatable = false)
    private Instant completedAt;
}
