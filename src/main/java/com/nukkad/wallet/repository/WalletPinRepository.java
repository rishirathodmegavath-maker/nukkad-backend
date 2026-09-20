package com.nukkad.wallet.repository;

import com.nukkad.wallet.entity.WalletPin;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletPinRepository extends JpaRepository<WalletPin, String> {

    /**
     * Serializes PIN attempts per user. Without the row lock, N parallel guesses would all read the
     * same failed-attempt count and each be judged as "attempt 1", letting an attacker try far more
     * PINs than the lockout allows. Holding this lock across the read-check-increment makes the
     * counter exact, even with many concurrent requests.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM WalletPin p WHERE p.userId = :userId")
    Optional<WalletPin> findByUserIdForUpdate(@Param("userId") String userId);
}
