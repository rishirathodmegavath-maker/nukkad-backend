package com.nukkad.payment.repository;

import com.nukkad.payment.entity.Payment;
import com.nukkad.payment.entity.PaymentProviderType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    Page<Payment> findByWalletIdOrderByCreatedAtDesc(String walletId, Pageable pageable);

    Optional<Payment> findByWalletIdAndIdempotencyKey(String walletId, String idempotencyKey);

    Optional<Payment> findByProviderAndProviderReference(PaymentProviderType provider, String providerReference);

    /** Same rationale as {@code WalletRepository#findByIdForUpdate}: a real provider will deliver
     *  webhooks with retries, and two concurrent deliveries for the same payment must not both
     *  pass the state-transition check and both credit the wallet. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") String id);
}
