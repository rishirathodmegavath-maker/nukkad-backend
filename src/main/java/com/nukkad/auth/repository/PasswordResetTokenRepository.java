package com.nukkad.auth.repository;

import com.nukkad.auth.entity.PasswordResetToken;
import com.nukkad.auth.entity.ResetAudience;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Row-locked read for the admin confirm path: without it, two concurrent redemptions of the same
    // still-usable token can both pass the usable check before either commits, so one emailed link
    // would set the password twice. Explicit @Query because "ForUpdate" is not a Spring Data keyword
    // (same reason as RefreshTokenRepository#findByTokenHashForUpdate).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<PasswordResetToken> findByUserIdAndAudienceAndUsedAtIsNull(String userId, ResetAudience audience);
}
