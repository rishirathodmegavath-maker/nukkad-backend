package com.nukkad.auth.repository;

import com.nukkad.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(String userId);
    List<RefreshToken> findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(String userId);

    // Row-locked read for the rotate-on-refresh path: without this, two genuinely concurrent
    // refresh calls presenting the same still-valid token can both read revokedAt == null before
    // either commits its rotation, minting two independently valid sessions from one token instead
    // of one succeeding and the other being serialized behind it.
    // Explicit @Query, not name-derivation: "ForUpdate" isn't a Spring Data keyword, so a plain
    // `findByTokenHashForUpdate(String)` with only @Lock fails at startup trying (and failing) to
    // traverse a "forUpdate" property off of RefreshToken.tokenHash (a String) -- caught in the
    // final pre-push review when the app wouldn't boot. Mirrors WalletRepository#findByIdForUpdate.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
