package com.nukkad.payment.entity;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * An external payment attempt against a wallet — separate from {@code WalletTransaction} because
 * a payment can fail before any money ever moves; only a payment that reaches {@code SUCCESS}
 * produces a ledger entry (linked back here via {@code walletTransactionId}). Not reachable from
 * any controller in V1 — see the implementation report for why building a public endpoint without
 * a real gateway would mean pretending payments work.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "wallet_id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private String walletId;

    @Column(name = "amount_minor_units", nullable = false, updatable = false)
    private long amountMinorUnits;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    @Builder.Default
    private PaymentProviderType provider = PaymentProviderType.NONE;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "wallet_transaction_id", columnDefinition = "CHAR(36)")
    private String walletTransactionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
