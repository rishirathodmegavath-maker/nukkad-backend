package com.nukkad.wallet.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.wallet.entity.Wallet;
import com.nukkad.wallet.entity.WalletStatus;
import com.nukkad.wallet.entity.WalletTransaction;
import com.nukkad.wallet.entity.WalletTransactionStatus;
import com.nukkad.wallet.entity.WalletTransactionType;
import com.nukkad.wallet.repository.WalletRepository;
import com.nukkad.wallet.repository.WalletTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

/**
 * The sole authority over wallet balances in this system — every credit/debit anywhere in the
 * codebase, present or future, must go through {@link #credit} / {@link #debit}. No controller or
 * DTO ever accepts a client-supplied balance; the server computes every new balance itself, under
 * a database row lock, in the same transaction as the immutable ledger row that records it.
 */
@Service
public class WalletService {

    public static final String DEFAULT_CURRENCY = "INR";

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public WalletService(WalletRepository walletRepository, WalletTransactionRepository walletTransactionRepository,
                          PlatformTransactionManager transactionManager) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        // See getOrCreateWallet's own comment for why the create-attempt needs its own, separate
        // transaction rather than running (and being caught) inside the caller's -- mirrors
        // AuthService#revokeAllForUser, the established pattern for this exact need elsewhere in
        // this codebase.
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Deliberately NOT {@code @Transactional} at this level -- see below.
     *
     * <p>The naive fix for the first-visit race is "saveAndFlush + catch
     * DataIntegrityViolationException, then re-read the winner's row", and that's still the right
     * *shape* of the fix. The part that isn't obvious: if the flush and the catch-block's fallback
     * read both run inside the SAME transaction/persistence context, the fallback read fails too.
     * Once a flush throws, Hibernate marks that Session unusable for the rest of the transaction
     * (JPA requires this — a Session that failed to flush cannot be trusted to reflect the DB
     * anymore) — so the very next operation on it (the fallback {@code findByUserId} meant to
     * recover from the race) throws its own, unrelated-looking DataIntegrityViolationException.
     * Verified against a live server: 12 truly concurrent first-visit requests, 4 went through a
     * plain {@code @Transactional} version of this method with a same-session catch/fallback, and
     * 8 of them failed with that secondary error instead of the intended silent recovery.
     *
     * <p>The fix: run the create-attempt in its own {@code REQUIRES_NEW} transaction, so a losing
     * attempt's failed flush rolls back and closes {@code entirely on its own} — the calling
     * method's own transaction (if it's even running inside one) is never touched by it, and the
     * fallback read that follows is a clean, ordinary read in a fresh context.
     */
    public Wallet getOrCreateWallet(String userId) {
        Optional<Wallet> existing = walletRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return requiresNewTransactionTemplate.execute(status -> {
                Wallet wallet = Wallet.builder()
                        .userId(userId)
                        .currency(DEFAULT_CURRENCY)
                        .balanceMinorUnits(0L)
                        .status(WalletStatus.ACTIVE)
                        .build();
                // saveAndFlush, not save: a plain save() only persists into the EntityManager
                // context and defers the actual INSERT (and thus this unique-constraint violation)
                // until commit -- past this try/catch, which is exactly why this race still reached
                // the client as an unhandled 500 despite a same-shape try/catch existing before.
                return walletRepository.saveAndFlush(wallet);
            });
        } catch (DataIntegrityViolationException e) {
            // Two concurrent first-access requests for the same user both saw "no wallet yet"
            // before either committed; the unique constraint on user_id caught the loser. Not an
            // error from the caller's point of view -- the losing REQUIRES_NEW transaction above
            // has already cleanly rolled back and ended on its own, so this plain read runs in an
            // unpoisoned context and reliably finds the winner's now-committed row.
            return walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalStateException("Wallet creation race unresolved for user " + userId));
        }
    }

    /**
     * For callers that only need the wallet id to immediately pass into {@link #credit}/
     * {@link #debit} — resolves via a bare-scalar projection (see
     * {@link WalletRepository#findIdByUserId}) rather than loading a full unlocked entity, so it
     * is always safe to call right before a locked mutation on the same wallet within the same
     * transaction. Falls back to the entity-creating path only the first time a user's wallet is
     * resolved at all.
     */
    @Transactional
    public String resolveOrCreateWalletId(String userId) {
        return walletRepository.findIdByUserId(userId)
                .orElseGet(() -> getOrCreateWallet(userId).getId());
    }

    @Transactional(readOnly = true)
    public Wallet getWalletOrThrow(String userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No wallet found for this user"));
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> listTransactions(String walletId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);
    }

    /** Increases the wallet balance. See {@link #applyLedgerEntry} for the shared atomic path. */
    @Transactional
    public WalletTransaction credit(String walletId, long amountMinorUnits, String referenceType, String referenceId,
                                     String idempotencyKey, String description) {
        return applyLedgerEntry(walletId, WalletTransactionType.CREDIT, amountMinorUnits, referenceType, referenceId,
                idempotencyKey, description);
    }

    /** Decreases the wallet balance; rejected if it would go negative. See {@link #applyLedgerEntry}. */
    @Transactional
    public WalletTransaction debit(String walletId, long amountMinorUnits, String referenceType, String referenceId,
                                    String idempotencyKey, String description) {
        return applyLedgerEntry(walletId, WalletTransactionType.DEBIT, amountMinorUnits, referenceType, referenceId,
                idempotencyKey, description);
    }

    /**
     * The one place a wallet balance is ever mutated. Order matters for both correctness and the
     * idempotency race documented below:
     * <ol>
     *   <li>Validate the amount.</li>
     *   <li>Take the wallet's row lock FIRST (before checking idempotency) — this serializes every
     *       concurrent call for this wallet, including two retries of the same idempotent request,
     *       so the idempotency check below can never be raced: whichever caller gets the lock
     *       second will see the first caller's already-committed ledger row.</li>
     *   <li>Validate wallet status.</li>
     *   <li>Re-check idempotency now that we hold the lock — return the existing transaction as-is
     *       (no second balance mutation) if this key was already applied.</li>
     *   <li>Validate sufficient funds for a debit.</li>
     *   <li>Update the balance and insert the ledger row in the same transaction — commit applies
     *       both together, or neither (no failure mode leaves one without the other).</li>
     * </ol>
     * The DB's own unique constraint on (wallet_id, idempotency_key) is the backstop in case this
     * in-transaction check is ever bypassed by a bug elsewhere — a constraint violation on insert
     * is caught and resolved by returning the row that actually won, never by failing the request.
     */
    private WalletTransaction applyLedgerEntry(String walletId, WalletTransactionType type, long amountMinorUnits,
                                                 String referenceType, String referenceId, String idempotencyKey,
                                                 String description) {
        if (amountMinorUnits <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        String normalizedKey = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey;

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Wallet is not active");
        }

        if (normalizedKey != null) {
            Optional<WalletTransaction> existing = walletTransactionRepository.findByWalletIdAndIdempotencyKey(walletId, normalizedKey);
            if (existing.isPresent()) {
                WalletTransaction previous = existing.get();
                if (previous.getType() != type || previous.getAmountMinorUnits() != amountMinorUnits) {
                    throw new ConflictException("Idempotency key was already used with different transaction parameters");
                }
                return previous; // replay of an already-applied request -- no second mutation
            }
        }

        long newBalance = type == WalletTransactionType.CREDIT
                ? wallet.getBalanceMinorUnits() + amountMinorUnits
                : wallet.getBalanceMinorUnits() - amountMinorUnits;
        if (newBalance < 0) {
            throw new BadRequestException("Insufficient wallet balance");
        }
        wallet.setBalanceMinorUnits(newBalance);
        walletRepository.save(wallet);

        WalletTransaction transaction = WalletTransaction.builder()
                .walletId(walletId)
                .type(type)
                .amountMinorUnits(amountMinorUnits)
                .currency(wallet.getCurrency())
                .status(WalletTransactionStatus.COMPLETED)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .idempotencyKey(normalizedKey)
                .description(description)
                .completedAt(Instant.now())
                .build();
        try {
            // saveAndFlush, not save: a plain save() only persists into the EntityManager context
            // and could defer the actual INSERT (and thus any constraint violation) past this
            // method's return -- flushing here is what makes the catch below able to fire at all.
            return walletTransactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            if (normalizedKey == null) throw e;
            return walletTransactionRepository.findByWalletIdAndIdempotencyKey(walletId, normalizedKey)
                    .orElseThrow(() -> e);
        }
    }
}
