package com.nukkad.payment.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.payment.entity.Payment;
import com.nukkad.payment.entity.PaymentProviderType;
import com.nukkad.payment.entity.PaymentStatus;
import com.nukkad.payment.repository.PaymentRepository;
import com.nukkad.wallet.entity.Wallet;
import com.nukkad.wallet.repository.WalletRepository;
import com.nukkad.wallet.service.WalletService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * NOT reachable from any controller in V1 — see the Wallet/Payments implementation report. This
 * class exists so that when a real payment gateway is chosen, its webhook handler has a correct,
 * already-tested state machine and ledger-crediting path to call into ({@link #recordSuccess}
 * etc.) rather than needing to invent one under time pressure alongside the provider integration.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public PaymentService(PaymentRepository paymentRepository, WalletRepository walletRepository,
                           WalletService walletService, AuditService auditService,
                           NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Payment create(String walletId, long amountMinorUnits, String currency, String idempotencyKey) {
        if (amountMinorUnits <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        if (!WalletService.DEFAULT_CURRENCY.equals(currency)) {
            throw new BadRequestException("Unsupported currency: " + currency);
        }
        String normalizedKey = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey;
        if (normalizedKey != null) {
            Optional<Payment> existing = paymentRepository.findByWalletIdAndIdempotencyKey(walletId, normalizedKey);
            if (existing.isPresent()) return existing.get();
        }
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));

        Payment payment = Payment.builder()
                .walletId(walletId)
                .amountMinorUnits(amountMinorUnits)
                .currency(currency)
                .status(PaymentStatus.CREATED)
                .provider(PaymentProviderType.NONE)
                .idempotencyKey(normalizedKey)
                .build();
        try {
            payment = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            if (normalizedKey == null) throw e;
            payment = paymentRepository.findByWalletIdAndIdempotencyKey(walletId, normalizedKey).orElseThrow(() -> e);
        }
        auditService.log(wallet.getUserId(), AuditAction.PAYMENT_INITIATED, "Payment", payment.getId(), null,
                Map.of("amountMinorUnits", amountMinorUnits, "currency", currency));
        return payment;
    }

    @Transactional
    public Payment markPending(String paymentId, String providerReference) {
        Payment payment = lockOrThrow(paymentId);
        requireTransition(payment, PaymentStatus.PENDING);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProviderReference(providerReference);
        return paymentRepository.save(payment);
    }

    /** Only a verified provider webhook (once a real provider exists) should ever call this — a
     *  client-side "payment succeeded" callback is never sufficient by itself. Credits the wallet
     *  atomically as part of the same transition; the ledger entry is keyed off the payment id so
     *  a duplicate call (e.g. a retried webhook) can never credit the wallet twice. */
    @Transactional
    public Payment recordSuccess(String paymentId) {
        Payment payment = lockOrThrow(paymentId);
        requireTransition(payment, PaymentStatus.SUCCESS);

        var walletTransaction = walletService.credit(payment.getWalletId(), payment.getAmountMinorUnits(),
                "PAYMENT", payment.getId(), "payment:" + payment.getId(),
                "Payment " + payment.getId());

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setWalletTransactionId(walletTransaction.getId());
        payment.setCompletedAt(Instant.now());
        Payment saved = paymentRepository.save(payment);

        Wallet wallet = walletRepository.findById(payment.getWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + payment.getWalletId()));
        auditService.log(wallet.getUserId(), AuditAction.PAYMENT_SUCCEEDED, "Payment", paymentId, null,
                Map.of("amountMinorUnits", payment.getAmountMinorUnits()));
        notificationService.notify(wallet.getUserId(), NotificationType.wallet, "Payment successful",
                "Your payment was successful and your wallet has been credited.", paymentId, null);
        return saved;
    }

    @Transactional
    public Payment recordFailure(String paymentId, String reason) {
        Payment payment = lockOrThrow(paymentId);
        requireTransition(payment, PaymentStatus.FAILED);
        payment.setStatus(PaymentStatus.FAILED);
        Payment saved = paymentRepository.save(payment);

        Wallet wallet = walletRepository.findById(payment.getWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + payment.getWalletId()));
        auditService.log(wallet.getUserId(), AuditAction.PAYMENT_FAILED, "Payment", paymentId, null,
                reason == null ? null : Map.of("reason", reason));
        return saved;
    }

    @Transactional
    public Payment cancel(String paymentId) {
        Payment payment = lockOrThrow(paymentId);
        requireTransition(payment, PaymentStatus.CANCELLED);
        payment.setStatus(PaymentStatus.CANCELLED);
        return paymentRepository.save(payment);
    }

    private void requireTransition(Payment payment, PaymentStatus target) {
        if (!payment.getStatus().canTransitionTo(target)) {
            throw new ConflictException("Cannot transition payment from " + payment.getStatus() + " to " + target);
        }
    }

    private Payment lockOrThrow(String id) {
        return paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }
}
