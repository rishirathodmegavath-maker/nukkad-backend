package com.nukkad.wallet.repository;

import com.nukkad.wallet.entity.WalletTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(String walletId, Pageable pageable);

    /**
     * {@code @Lock}, not a plain read: this is MySQL's well-known REPEATABLE READ gotcha applied to
     * idempotency checking. {@code WalletService#applyLedgerEntry} already holds the wallet row's
     * own lock before calling this, which correctly serializes concurrent callers one at a time —
     * but a PLAIN (non-locking) SELECT in InnoDB under REPEATABLE READ is pinned to the
     * transaction's first-read snapshot, not to "right now", so a waiter that's just been granted
     * the wallet lock can still see this query return empty for a key another caller already
     * committed moments earlier. Verified against a live server: 8 truly concurrent identical
     * admin wallet-adjustment requests (same idempotency key), 6 of 8 hit exactly this — a
     * duplicate-looking DataIntegrityViolationException instead of the intended silent replay.
     * {@code @Lock} forces InnoDB to read the latest committed data regardless of snapshot, exactly
     * like the fix for {@code WalletRepository#findByIdForUpdate} elsewhere in this codebase.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletTransaction> findByWalletIdAndIdempotencyKey(String walletId, String idempotencyKey);
}
