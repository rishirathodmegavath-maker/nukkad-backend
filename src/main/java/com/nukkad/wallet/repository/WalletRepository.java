package com.nukkad.wallet.repository;

import com.nukkad.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, String> {

    Optional<Wallet> findByUserId(String userId);

    /**
     * A bare-scalar projection, NOT a managed {@code Wallet} entity — deliberately, so that
     * resolving "which wallet does this user have" before a locked credit/debit never populates
     * the Hibernate session with an unlocked copy of that row first. Loading the full entity here
     * and then requesting {@link #findByIdForUpdate} on the same id within the same transaction
     * would let Hibernate satisfy the locked query from the session's identity map without ever
     * re-issuing it as a {@code SELECT ... FOR UPDATE} — silently defeating the lock. Callers that
     * only need the id to pass into {@code WalletService#credit}/{@code #debit} must use this, not
     * {@link #findByUserId}.
     */
    @Query("SELECT w.id FROM Wallet w WHERE w.userId = :userId")
    Optional<String> findIdByUserId(@Param("userId") String userId);

    /**
     * Serializes concurrent credits/debits against the same wallet: without this, two
     * simultaneous requests could both read the balance before either commits, letting one
     * overwrite the other's update or letting two debits both pass a sufficient-funds check
     * against a balance neither of them has actually spent yet. Holding this lock for the whole
     * balance-mutation transaction (see WalletService) makes the read-validate-write sequence
     * atomic at the database level — correct even across multiple application instances.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") String id);
}
