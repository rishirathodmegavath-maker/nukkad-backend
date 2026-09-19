package com.nukkad.payment.service;

import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.payment.entity.Payment;
import com.nukkad.payment.entity.PaymentStatus;
import com.nukkad.payment.repository.PaymentRepository;
import com.nukkad.wallet.entity.Wallet;
import com.nukkad.wallet.entity.WalletStatus;
import com.nukkad.wallet.entity.WalletTransaction;
import com.nukkad.wallet.entity.WalletTransactionType;
import com.nukkad.wallet.repository.WalletRepository;
import com.nukkad.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentService is not reachable from any controller in V1 (see the implementation report) —
 * these tests exist so the state machine and the wallet-crediting path are proven correct before
 * a real provider's webhook handler is ever built on top of them.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletService walletService;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    private PaymentService service() {
        return new PaymentService(paymentRepository, walletRepository, walletService, auditService, notificationService);
    }

    private Wallet wallet(String id, String userId) {
        return Wallet.builder().id(id).userId(userId).currency("INR").balanceMinorUnits(0).status(WalletStatus.ACTIVE).build();
    }

    private Payment payment(String id, PaymentStatus status) {
        return Payment.builder().id(id).walletId("w1").amountMinorUnits(500).currency("INR").status(status).build();
    }

    @Test
    void createStartsAPaymentInCreatedStatus() {
        when(walletRepository.findById("w1")).thenReturn(Optional.of(wallet("w1", "user-1")));
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = service().create("w1", 500, "INR", null);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(result.getAmountMinorUnits()).isEqualTo(500);
    }

    @Test
    void createRejectsUnsupportedCurrency() {
        assertThatThrownBy(() -> service().create("w1", 500, "USD", null))
                .isInstanceOf(BadRequestException.class);
        verify(walletRepository, never()).findById(any());
    }

    @Test
    void createRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> service().create("w1", 0, "INR", null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void duplicateCreateIdempotencyKeyReturnsExistingPaymentWithoutCreatingAnother() {
        Payment existing = payment("p1", PaymentStatus.CREATED);
        when(paymentRepository.findByWalletIdAndIdempotencyKey("w1", "key-1")).thenReturn(Optional.of(existing));

        Payment result = service().create("w1", 500, "INR", "key-1");

        assertThat(result).isSameAs(existing);
        verify(walletRepository, never()).findById(any());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void validTransitionCreatedToPendingSucceeds() {
        Payment p = payment("p1", PaymentStatus.CREATED);
        when(paymentRepository.findByIdForUpdate("p1")).thenReturn(Optional.of(p));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = service().markPending("p1", "provider-ref-123");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getProviderReference()).isEqualTo("provider-ref-123");
    }

    @Test
    void recordSuccessCreditsTheWalletAuditsAndNotifies() {
        Payment p = payment("p1", PaymentStatus.PENDING);
        WalletTransaction txn = WalletTransaction.builder().id("t1").walletId("w1").type(WalletTransactionType.CREDIT).amountMinorUnits(500).currency("INR").build();
        when(paymentRepository.findByIdForUpdate("p1")).thenReturn(Optional.of(p));
        when(walletService.credit(eq("w1"), eq(500L), eq("PAYMENT"), eq("p1"), eq("payment:p1"), any())).thenReturn(txn);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletRepository.findById("w1")).thenReturn(Optional.of(wallet("w1", "user-1")));

        Payment result = service().recordSuccess("p1");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getWalletTransactionId()).isEqualTo("t1");
        verify(auditService).log(eq("user-1"), any(), eq("Payment"), eq("p1"), any(), any());
        verify(notificationService).notify(eq("user-1"), eq(NotificationType.wallet), any(), any(), eq("p1"), any());
    }

    @Test
    void recordSuccessUsesADeterministicPerPaymentIdempotencyKeySoARetriedCallCannotDoubleCredit() {
        Payment p = payment("p1", PaymentStatus.PENDING);
        when(paymentRepository.findByIdForUpdate("p1")).thenReturn(Optional.of(p));
        when(walletService.credit(any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(WalletTransaction.builder().id("t1").walletId("w1").type(WalletTransactionType.CREDIT).amountMinorUnits(500).currency("INR").build());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletRepository.findById("w1")).thenReturn(Optional.of(wallet("w1", "user-1")));

        service().recordSuccess("p1");

        verify(walletService).credit("w1", 500L, "PAYMENT", "p1", "payment:p1", "Payment p1");
    }

    @Test
    void cannotTransitionFromSuccessBackToPending() {
        Payment p = payment("p1", PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdForUpdate("p1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service().markPending("p1", "ref"))
                .isInstanceOf(ConflictException.class);
        verify(walletService, never()).credit(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void cannotTransitionFromRefundedBackToSuccess() {
        Payment p = payment("p1", PaymentStatus.REFUNDED);
        when(paymentRepository.findByIdForUpdate("p1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service().recordSuccess("p1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotTransitionFromFailedToSuccess() {
        Payment p = payment("p1", PaymentStatus.FAILED);
        when(paymentRepository.findByIdForUpdate("p1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service().recordSuccess("p1"))
                .isInstanceOf(ConflictException.class);
        verify(walletService, never()).credit(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void aSecondRecordSuccessCallOnAnAlreadySuccessfulPaymentIsRejectedNotDoubleCredited() {
        Payment p = payment("p1", PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdForUpdate("p1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service().recordSuccess("p1"))
                .isInstanceOf(ConflictException.class);
        verify(walletService, never()).credit(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void successfulPaymentCanLaterBeRefunded() {
        Payment p = payment("p1", PaymentStatus.SUCCESS);
        assertThat(p.getStatus().canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
    }

    @Test
    void missingPaymentThrowsNotFound() {
        when(paymentRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().recordSuccess("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createConstraintViolationRaceIsResolvedByReturningTheWinningRow() {
        Payment winner = payment("p1", PaymentStatus.CREATED);
        when(walletRepository.findById("w1")).thenReturn(Optional.of(wallet("w1", "user-1")));
        when(paymentRepository.findByWalletIdAndIdempotencyKey("w1", "key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(paymentRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        Payment result = service().create("w1", 500, "INR", "key-1");

        assertThat(result).isSameAs(winner);
    }
}
