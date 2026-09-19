package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminWithdrawalDto;
import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.wallet.entity.WithdrawalRequest;
import com.nukkad.wallet.entity.WithdrawalStatus;
import com.nukkad.wallet.repository.WithdrawalRequestRepository;
import com.nukkad.wallet.service.WithdrawalService;
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

/** Covers the admin-facing half of the withdrawal flow: approve finalizes a hold with no further
 *  ledger action, reject reuses {@link WithdrawalService#refund}, and an admin can never decide
 *  their own request — mirroring AdminWalletService's identical self-adjustment guard. */
@ExtendWith(MockitoExtension.class)
class AdminWithdrawalServiceTest {

    @Mock private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private WithdrawalService withdrawalService;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;

    private final AdminMapper adminMapper = new AdminMapper();

    private AdminWithdrawalService service() {
        return new AdminWithdrawalService(withdrawalRequestRepository, withdrawalService, userRepository, adminMapper,
                auditService, notificationService);
    }

    private WithdrawalRequest pendingRequest() {
        return WithdrawalRequest.builder().id("wd1").userId("user1").walletId("wallet1")
                .amountMinorUnits(1000).currency("INR").status(WithdrawalStatus.PENDING).build();
    }

    @Test
    void approvingFinalizesTheHoldWithNoFurtherLedgerAction() {
        when(withdrawalRequestRepository.findByIdForUpdate("wd1")).thenReturn(Optional.of(pendingRequest()));
        when(withdrawalRequestRepository.saveAndFlush(any(WithdrawalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("user1")).thenReturn(Optional.of(User.builder().id("user1").name("Founder").build()));

        AdminWithdrawalDto dto = service().approve("admin1", "wd1", "1.2.3.4");

        assertThat(dto.status()).isEqualTo("APPROVED");
        verify(auditService).log(eq("admin1"), eq(AuditAction.WALLET_WITHDRAWAL_APPROVED), eq("WithdrawalRequest"), eq("wd1"), eq("1.2.3.4"));
        verify(notificationService).notify(eq("user1"), any(), anyString(), anyString(), eq("wd1"), eq("admin1"));
    }

    @Test
    void rejectingReusesTheSharedRefundPath() {
        WithdrawalRequest request = pendingRequest();
        when(withdrawalRequestRepository.findByIdForUpdate("wd1")).thenReturn(Optional.of(request));
        WithdrawalRequest rejected = pendingRequest();
        rejected.setStatus(WithdrawalStatus.REJECTED);
        rejected.setDecisionNote("Suspicious activity");
        when(withdrawalService.refund(eq(request), eq(WithdrawalStatus.REJECTED), eq("admin1"), eq("Suspicious activity")))
                .thenReturn(rejected);
        when(userRepository.findById("user1")).thenReturn(Optional.of(User.builder().id("user1").name("Founder").build()));

        AdminWithdrawalDto dto = service().reject("admin1", "wd1", "Suspicious activity", "1.2.3.4");

        assertThat(dto.status()).isEqualTo("REJECTED");
        assertThat(dto.decisionNote()).isEqualTo("Suspicious activity");
        verify(auditService).log(eq("admin1"), eq(AuditAction.WALLET_WITHDRAWAL_REJECTED), eq("WithdrawalRequest"), eq("wd1"), eq("1.2.3.4"), any());
    }

    @Test
    void anAdminCannotDecideTheirOwnWithdrawalRequest() {
        WithdrawalRequest ownRequest = WithdrawalRequest.builder().id("wd2").userId("admin1").walletId("wallet2")
                .amountMinorUnits(500).currency("INR").status(WithdrawalStatus.PENDING).build();
        when(withdrawalRequestRepository.findByIdForUpdate("wd2")).thenReturn(Optional.of(ownRequest));

        assertThatThrownBy(() -> service().approve("admin1", "wd2", "1.2.3.4")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void anAlreadyDecidedRequestCannotBeApprovedAgain() {
        WithdrawalRequest decided = pendingRequest();
        decided.setStatus(WithdrawalStatus.APPROVED);
        when(withdrawalRequestRepository.findByIdForUpdate("wd1")).thenReturn(Optional.of(decided));

        assertThatThrownBy(() -> service().approve("admin1", "wd1", "1.2.3.4")).isInstanceOf(ConflictException.class);
    }
}
