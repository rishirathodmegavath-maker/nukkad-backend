package com.nukkad.wallet.repository;

import com.nukkad.wallet.entity.WithdrawalRequest;
import com.nukkad.wallet.entity.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, String> {

    Page<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<WithdrawalRequest> findByStatusOrderByCreatedAtDesc(WithdrawalStatus status, Pageable pageable);

    Page<WithdrawalRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Serializes concurrent decisions on the same request: without this, two simultaneous
     * approve/reject calls (or a user's cancel racing an admin's decision) could both read
     * status=PENDING before either commits, so both apply — the loser's decision should instead
     * see the already-decided status and be rejected with a clean error.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM WithdrawalRequest r WHERE r.id = :id")
    Optional<WithdrawalRequest> findByIdForUpdate(@Param("id") String id);

    long countByStatus(WithdrawalStatus status);
}
