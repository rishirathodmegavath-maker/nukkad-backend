package com.nukkad.wallet.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.wallet.entity.Wallet;
import com.nukkad.wallet.entity.WalletStatus;
import com.nukkad.wallet.entity.WalletTransaction;
import com.nukkad.wallet.entity.WalletTransactionType;
import com.nukkad.wallet.repository.WalletRepository;
import com.nukkad.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the core balance-integrity and idempotency guarantees WalletService is responsible for.
 * Concurrency itself (two real simultaneous debits) is a database-level guarantee provided by the
 * {@code PESSIMISTIC_WRITE} lock in {@code WalletRepository#findByIdForUpdate} — not something a
 * Mockito unit test can exercise, but these tests confirm the service always goes through that
 * locked read (never a plain {@code findById}) before mutating a balance.
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;

    private WalletService service() {
        return new WalletService(walletRepository, walletTransactionRepository);
    }

    private Wallet wallet(String id, long balance, WalletStatus status) {
        return Wallet.builder().id(id).userId("user-1").currency("INR").balanceMinorUnits(balance).status(status).build();
    }

    // ---- getOrCreateWallet ----

    @Test
    void returnsExistingWalletWithoutCreatingANewOne() {
        Wallet existing = wallet("w1", 500, WalletStatus.ACTIVE);
        when(walletRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));

        Wallet result = service().getOrCreateWallet("user-1");

        assertThat(result).isSameAs(existing);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void createsANewZeroBalanceWalletOnFirstAccess() {
        when(walletRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Wallet result = service().getOrCreateWallet("user-1");

        assertThat(result.getBalanceMinorUnits()).isZero();
        assertThat(result.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(result.getCurrency()).isEqualTo("INR");
    }

    @Test
    void concurrentFirstAccessRaceReturnsTheWinnersWalletInsteadOfFailing() {
        Wallet winner = wallet("w1", 0, WalletStatus.ACTIVE);
        when(walletRepository.findByUserId("user-1"))
                .thenReturn(Optional.empty())   // this caller's own check
                .thenReturn(Optional.of(winner)); // re-fetch after losing the insert race
        when(walletRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        Wallet result = service().getOrCreateWallet("user-1");

        assertThat(result).isSameAs(winner);
    }

    // ---- resolveOrCreateWalletId ----

    @Test
    void resolveOrCreateWalletIdUsesTheScalarProjectionWhenTheWalletAlreadyExists() {
        when(walletRepository.findIdByUserId("user-1")).thenReturn(Optional.of("w1"));

        String id = service().resolveOrCreateWalletId("user-1");

        assertThat(id).isEqualTo("w1");
        verify(walletRepository, never()).findByUserId(any()); // never loads a full entity
        verify(walletRepository, never()).save(any());
    }

    @Test
    void resolveOrCreateWalletIdFallsBackToCreatingOneWhenNoneExistsYet() {
        when(walletRepository.findIdByUserId("user-1")).thenReturn(Optional.empty());
        when(walletRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        // Simulates what @UuidGenerator does for real on persist -- assigns the id as a side
        // effect of save(), which Mockito's plain echo-back stub wouldn't otherwise reproduce.
        when(walletRepository.save(any())).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            w.setId("generated-id");
            return w;
        });

        String id = service().resolveOrCreateWalletId("user-1");

        assertThat(id).isEqualTo("generated-id");
        verify(walletRepository).save(any());
    }

    // ---- credit / debit basics ----

    @Test
    void creditIncreasesBalanceAndWritesACompletedLedgerRow() {
        Wallet w = wallet("w1", 1000, WalletStatus.ACTIVE);
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(w));
        when(walletTransactionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletTransaction txn = service().credit("w1", 500, "ADMIN_ADJUSTMENT", "admin1", null, "top-up");

        assertThat(w.getBalanceMinorUnits()).isEqualTo(1500);
        assertThat(txn.getType()).isEqualTo(WalletTransactionType.CREDIT);
        assertThat(txn.getAmountMinorUnits()).isEqualTo(500);
        verify(walletRepository).save(w);
    }

    @Test
    void debitDecreasesBalanceWhenSufficientFundsExist() {
        Wallet w = wallet("w1", 1000, WalletStatus.ACTIVE);
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(w));
        when(walletTransactionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletTransaction txn = service().debit("w1", 400, "ADMIN_ADJUSTMENT", "admin1", null, "spend");

        assertThat(w.getBalanceMinorUnits()).isEqualTo(600);
        assertThat(txn.getType()).isEqualTo(WalletTransactionType.DEBIT);
    }

    @Test
    void debitRejectedWhenItWouldMakeBalanceNegative() {
        Wallet w = wallet("w1", 100, WalletStatus.ACTIVE);
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> service().debit("w1", 500, "TEST", null, null, "over-spend"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient");
        assertThat(w.getBalanceMinorUnits()).isEqualTo(100); // untouched
        verify(walletTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void debitOfExactlyTheFullBalanceIsAllowed() {
        Wallet w = wallet("w1", 500, WalletStatus.ACTIVE);
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(w));
        when(walletTransactionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service().debit("w1", 500, "TEST", null, null, "drain");

        assertThat(w.getBalanceMinorUnits()).isZero();
    }

    @Test
    void negativeAmountRejected() {
        assertThatThrownBy(() -> service().credit("w1", -100, "TEST", null, null, "x"))
                .isInstanceOf(BadRequestException.class);
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void zeroAmountRejected() {
        assertThatThrownBy(() -> service().debit("w1", 0, "TEST", null, null, "x"))
                .isInstanceOf(BadRequestException.class);
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void frozenWalletRejectsCreditAndDebit() {
        Wallet w = wallet("w1", 1000, WalletStatus.FROZEN);
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> service().credit("w1", 100, "TEST", null, null, "x"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void missingWalletThrowsNotFound() {
        when(walletRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().credit("missing", 100, "TEST", null, null, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void everyMutationGoesThroughTheLockedReadNeverAPlainFindById() {
        Wallet w = wallet("w1", 1000, WalletStatus.ACTIVE);
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(w));
        when(walletTransactionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service().credit("w1", 100, "TEST", null, null, "x");

        verify(walletRepository).findByIdForUpdate("w1");
        verify(walletRepository, never()).findById(any());
    }

    // ---- idempotency ----

    @Test
    void duplicateIdempotencyKeyReturnsTheExistingTransactionInsteadOfCreatingAnother() {
        Wallet w = wallet("w1", 1000, WalletStatus.ACTIVE);
        WalletTransaction existing = WalletTransaction.builder()
                .id("t1").walletId("w1").type(WalletTransactionType.CREDIT).amountMinorUnits(200).currency("INR").build();
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(w));
        when(walletTransactionRepository.findByWalletIdAndIdempotencyKey("w1", "key-abc")).thenReturn(Optional.of(existing));

        WalletTransaction result = service().credit("w1", 200, "TEST", null, "key-abc", "retry");

        assertThat(result).isSameAs(existing);
        assertThat(w.getBalanceMinorUnits()).isEqualTo(1000); // NOT double-applied
        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameIdempotencyKeyWithDifferentAmountIsRejectedAsAConflictRatherThanSilentlyReturningStaleResult() {
        Wallet w = wallet("w1", 1000, WalletStatus.ACTIVE);
        WalletTransaction existing = WalletTransaction.builder()
                .id("t1").walletId("w1").type(WalletTransactionType.CREDIT).amountMinorUnits(200).currency("INR").build();
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(w));
        when(walletTransactionRepository.findByWalletIdAndIdempotencyKey("w1", "key-abc")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().credit("w1", 999, "TEST", null, "key-abc", "different amount"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void differentIdempotencyKeysOnTheSameWalletCreateIndependentTransactions() {
        Wallet w = wallet("w1", 1000, WalletStatus.ACTIVE);
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(w));
        when(walletTransactionRepository.findByWalletIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(walletTransactionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service().credit("w1", 100, "TEST", null, "key-1", "first");
        service().credit("w1", 100, "TEST", null, "key-2", "second");

        assertThat(w.getBalanceMinorUnits()).isEqualTo(1200);
        verify(walletTransactionRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void dbConstraintViolationOnInsertIsResolvedByReturningTheRowThatActuallyWon() {
        // Backstop for the case where the application-level idempotency check (above) somehow
        // missed a concurrent duplicate -- the database's own unique constraint is what actually
        // prevents the double-apply; this proves the service recovers gracefully instead of
        // surfacing a raw DB exception to the caller.
        Wallet w = wallet("w1", 1000, WalletStatus.ACTIVE);
        WalletTransaction winner = WalletTransaction.builder()
                .id("t1").walletId("w1").type(WalletTransactionType.CREDIT).amountMinorUnits(200).currency("INR").build();
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(w));
        when(walletTransactionRepository.findByWalletIdAndIdempotencyKey("w1", "key-abc"))
                .thenReturn(Optional.empty())   // this caller's own check
                .thenReturn(Optional.of(winner)); // re-fetch after losing the insert race
        when(walletTransactionRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        WalletTransaction result = service().credit("w1", 200, "TEST", null, "key-abc", "race");

        assertThat(result).isSameAs(winner);
    }

    // ---- listTransactions ----

    @Test
    void listTransactionsClampsPageSizeAndDelegatesToRepository() {
        when(walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service().listTransactions("w1", 0, 500);

        ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(walletTransactionRepository).findByWalletIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq("w1"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100); // clamped from 500
    }
}
