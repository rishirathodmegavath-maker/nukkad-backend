package com.nukkad.wallet.repository;

import com.nukkad.wallet.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(String walletId, Pageable pageable);

    Optional<WalletTransaction> findByWalletIdAndIdempotencyKey(String walletId, String idempotencyKey);
}
