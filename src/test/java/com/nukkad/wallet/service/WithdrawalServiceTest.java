package com.nukkad.wallet.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.wallet.entity.WalletTransaction;
import com.nukkad.wallet.entity.WithdrawalRequest;
import com.nukkad.wallet.entity.WithdrawalStatus;
import com.nukkad.wallet.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the user-facing half of the withdrawal flow: requesting immediately holds the funds
 * (see WithdrawalRequest's class comment for why this is a DEBIT at request time, not at
 * approval), and cancelling refunds that hold — mirroring how AdminWithdrawalService's reject
 * reuses the exact same {@link WithdrawalService#refund} path.
 */
@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock private WalletService walletService;
    @Mock private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private AuditService auditService;

    private WithdrawalService service() {
        return new WithdrawalService(walletService, withdrawalRequestRepository, auditService);
    }

    private void stubSaveAssignsId(String id) {
        when(withdrawalRequestRepository.saveAndFlush(any(WithdrawalRequest.class))).thenAnswer(inv -> {
            WithdrawalRequest w = inv.getArgument(0);
            if (w.getId() == null) w.setId(id);
            return w;
        });
    }

    private WithdrawalRequest pendingRequest(String userId) {
        return WithdrawalRequest.builder().id("wd1").userId(userId).walletId("wallet1")
                .amountMinorUnits(1000).currency("INR").status(WithdrawalStatus.PENDING).build();
    }

    @Test
    void requestingAWithdrawalHoldsTheFundsImmediatelyAndLogsIt() {
        when(walletService.resolveOrCreateWalletId("user1")).thenReturn("wallet1");
        stubSaveAssignsId("wd1");
        when(walletService.debit(eq("wallet1"), eq(1000L), eq("WITHDRAWAL_HOLD"), anyString(), anyString(), anyString()))
                .thenReturn(WalletTransaction.builder().id("txn-hold").build());

        WithdrawalRequest result = service().request("user1", 1000, "Need it for rent");

        assertThat(result.getStatus()).isEqualTo(WithdrawalStatus.PENDING);
        assertThat(result.getHoldTransactionId()).isEqualTo("txn-hold");
        assertThat(result.getAmountMinorUnits()).isEqualTo(1000);
        verify(auditService).log(eq("user1"), eq(AuditAction.WALLET_WITHDRAWAL_REQUESTED), eq("WithdrawalRequest"), eq("wd1"), any());
    }

    @Test
    void onlyTheOwnerCanCancelTheirWithdrawalRequest() {
        when(withdrawalRequestRepository.findByIdForUpdate("wd1")).thenReturn(Optional.of(pendingRequest("user1")));

        assertThatThrownBy(() -> service().cancel("someoneElse", "wd1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancellingRefundsTheHeldAmount() {
        when(withdrawalRequestRepository.findByIdForUpdate("wd1")).thenReturn(Optional.of(pendingRequest("user1")));
        stubSaveAssignsId("wd1");
        when(walletService.credit(eq("wallet1"), eq(1000L), eq("WITHDRAWAL_REFUND"), eq("wd1"), anyString(), anyString()))
                .thenReturn(WalletTransaction.builder().id("txn-refund").build());

        WithdrawalRequest result = service().cancel("user1", "wd1");

        assertThat(result.getStatus()).isEqualTo(WithdrawalStatus.CANCELLED);
        assertThat(result.getRefundTransactionId()).isEqualTo("txn-refund");
    }

    @Test
    void anAlreadyDecidedRequestCannotBeCancelledAgain() {
        WithdrawalRequest decided = pendingRequest("user1");
        decided.setStatus(WithdrawalStatus.APPROVED);
        when(withdrawalRequestRepository.findByIdForUpdate("wd1")).thenReturn(Optional.of(decided));

        assertThatThrownBy(() -> service().cancel("user1", "wd1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void cancellingAnUnknownRequestThrowsNotFound() {
        when(withdrawalRequestRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().cancel("user1", "missing")).isInstanceOf(ResourceNotFoundException.class);
    }
}
