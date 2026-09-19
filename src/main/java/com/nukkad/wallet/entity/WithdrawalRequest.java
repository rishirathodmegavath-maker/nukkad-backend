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
 * Unlike {@link WalletTransaction} (append-only/immutable), this row is meant to change state —
 * PENDING through to a decision. The funds themselves still only ever move through
 * {@link com.nukkad.wallet.service.WalletService#credit}/{@code #debit}: {@code holdTransactionId}
 * is the DEBIT row created the moment a request is submitted (funds move immediately, so the same
 * balance can't be withdrawn twice while a decision is pending); {@code refundTransactionId} is
 * the CREDIT row created only if the request is rejected or cancelled. Approval performs no further
 * ledger action — the hold already moved the funds, and the actual payout happens manually outside
 * the app (no payment gateway is wired for payouts).
 */
@Entity
@Table(name = "withdrawal_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalRequest {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private String userId;

    @Column(name = "wallet_id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private String walletId;

    @Column(name = "amount_minor_units", nullable = false, updatable = false)
    private long amountMinorUnits;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.PENDING;

    @Column(name = "hold_transaction_id", columnDefinition = "CHAR(36)")
    private String holdTransactionId;

    @Column(name = "refund_transaction_id", columnDefinition = "CHAR(36)")
    private String refundTransactionId;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "decided_by_admin_id", columnDefinition = "CHAR(36)")
    private String decidedByAdminId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
