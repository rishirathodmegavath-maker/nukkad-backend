package com.nukkad.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * The wallet PIN of one member (absent until they set one). Only a peppered BCrypt hash is stored,
 * never the PIN. {@code failedAttempts}/{@code lockedUntil} are the brute-force lockout, persisted so
 * a restart can't reset them; {@code pinVersion} is bumped whenever the PIN changes or the wallet
 * locks, which instantly invalidates every unlock token already issued.
 */
@Entity
@Table(name = "wallet_pins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletPin {

    @Id
    @Column(name = "user_id", columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String userId;

    @Column(name = "pin_hash", nullable = false, length = 100)
    private String pinHash;

    @Column(name = "pin_version", nullable = false)
    @Builder.Default
    private int pinVersion = 1;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
