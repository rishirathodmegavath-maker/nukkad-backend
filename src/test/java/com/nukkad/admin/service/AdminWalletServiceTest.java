package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdjustWalletBalanceRequest;
import com.nukkad.admin.dto.AdminWalletDto;
import com.nukkad.admin.dto.SetWalletStatusRequest;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.wallet.entity.Wallet;
import com.nukkad.wallet.entity.WalletStatus;
import com.nukkad.wallet.mapper.WalletMapper;
import com.nukkad.wallet.repository.WalletRepository;
import com.nukkad.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * There is deliberately no "set balance" method on AdminWalletService to test the absence of --
 * every path here goes through WalletService#credit/#debit, so these tests focus on the
 * authorization/validation wrapper AdminWalletService adds on top of that (mandatory reason,
 * self-adjustment guard, audit, notification) rather than re-testing the ledger mechanics
 * already covered by WalletServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class AdminWalletServiceTest {

    @Mock private WalletService walletService;
    private final WalletMapper walletMapper = new WalletMapper();
    @Mock private WalletRepository walletRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    private AdminWalletService service() {
        return new AdminWalletService(walletService, walletMapper, walletRepository, userRepository, auditService, notificationService);
    }

    private User user(String id) {
        return User.builder().id(id).name("Target User").email(id + "@nukkad.test").passwordHash("hashed").build();
    }

    private Wallet wallet(String id, String userId, long balance) {
        return Wallet.builder().id(id).userId(userId).currency("INR").balanceMinorUnits(balance).status(WalletStatus.ACTIVE).build();
    }

    @Test
    void adminCannotAdjustTheirOwnWallet() {
        assertThatThrownBy(() -> service().adjustBalance("admin1", "admin1",
                new AdjustWalletBalanceRequest("CREDIT", 100, "INR", "self top-up", null), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        verify(walletService, never()).credit(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void creditAdjustmentCallsWalletServiceAuditsAndNotifiesTheTargetUser() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(walletService.resolveOrCreateWalletId("u1")).thenReturn("w1");
        when(walletService.getWalletOrThrow("u1")).thenReturn(wallet("w1", "u1", 1500));

        AdminWalletDto result = service().adjustBalance("admin1", "u1",
                new AdjustWalletBalanceRequest("CREDIT", 500, "INR", "goodwill credit", null), "127.0.0.1");

        verify(walletService).credit("w1", 500L, "ADMIN_ADJUSTMENT", "admin1", null, "goodwill credit");
        verify(auditService).log(eq("admin1"), any(), eq("Wallet"), eq("w1"), any(), any());
        verify(notificationService).notify(eq("u1"), eq(NotificationType.wallet), any(), any(), eq("w1"), eq("admin1"));
        assertThat(result.balanceMinorUnits()).isEqualTo(1500);
    }

    @Test
    void debitAdjustmentCallsWalletServiceDebit() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(walletService.resolveOrCreateWalletId("u1")).thenReturn("w1");
        when(walletService.getWalletOrThrow("u1")).thenReturn(wallet("w1", "u1", 600));

        service().adjustBalance("admin1", "u1",
                new AdjustWalletBalanceRequest("DEBIT", 400, "INR", "correcting a duplicate credit", null), "127.0.0.1");

        verify(walletService).debit("w1", 400L, "ADMIN_ADJUSTMENT", "admin1", null, "correcting a duplicate credit");
        verify(walletService, never()).credit(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void reasonIsRecordedInTheAuditDetails() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(walletService.resolveOrCreateWalletId("u1")).thenReturn("w1");
        when(walletService.getWalletOrThrow("u1")).thenReturn(wallet("w1", "u1", 1500));
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);

        service().adjustBalance("admin1", "u1",
                new AdjustWalletBalanceRequest("CREDIT", 500, "INR", "manual reconciliation", null), "127.0.0.1");

        verify(auditService).log(eq("admin1"), any(), eq("Wallet"), eq("w1"), any(), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue())
                .containsEntry("reason", "manual reconciliation")
                .containsEntry("direction", "CREDIT")
                .containsEntry("targetUserId", "u1");
    }

    @Test
    void invalidDirectionRejected() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));

        assertThatThrownBy(() -> service().adjustBalance("admin1", "u1",
                new AdjustWalletBalanceRequest("REFUND", 100, "INR", "oops", null), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        verify(walletService, never()).credit(any(), anyLong(), any(), any(), any(), any());
        verify(walletService, never()).debit(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void unsupportedCurrencyRejected() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));

        assertThatThrownBy(() -> service().adjustBalance("admin1", "u1",
                new AdjustWalletBalanceRequest("CREDIT", 100, "USD", "oops", null), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void adjustingAWalletForANonexistentUserThrowsNotFound() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().adjustBalance("admin1", "ghost",
                new AdjustWalletBalanceRequest("CREDIT", 100, "INR", "test", null), "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void idempotencyKeyIsForwardedToWalletService() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(walletService.resolveOrCreateWalletId("u1")).thenReturn("w1");
        when(walletService.getWalletOrThrow("u1")).thenReturn(wallet("w1", "u1", 1500));

        service().adjustBalance("admin1", "u1",
                new AdjustWalletBalanceRequest("CREDIT", 500, "INR", "top-up", "idem-key-1"), "127.0.0.1");

        verify(walletService).credit("w1", 500L, "ADMIN_ADJUSTMENT", "admin1", "idem-key-1", "top-up");
    }

    @Test
    void adjustBalanceResolvesTheWalletIdViaTheProjectionNeverTheEntityLoadingPath() {
        // Regression guard: calling walletService.getOrCreateWallet() (loads a full, unlocked
        // entity) before credit()/debit() on the SAME wallet within the SAME transaction lets
        // Hibernate satisfy the locked query from its session cache instead of re-acquiring the
        // row lock -- this silently defeated concurrent-debit protection in practice (verified via
        // a real two-concurrent-request test against MySQL) until adjustBalance was switched to
        // resolveOrCreateWalletId's bare-id projection. If this test ever fails, that regression
        // has come back.
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(walletService.resolveOrCreateWalletId("u1")).thenReturn("w1");
        when(walletService.getWalletOrThrow("u1")).thenReturn(wallet("w1", "u1", 1500));

        service().adjustBalance("admin1", "u1",
                new AdjustWalletBalanceRequest("CREDIT", 500, "INR", "top-up", null), "127.0.0.1");

        verify(walletService, never()).getOrCreateWallet(any());
    }

    @Test
    void getWalletLazilyCreatesOneForAUserWhoNeverHadOne() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(walletService.getOrCreateWallet("u1")).thenReturn(wallet("w1", "u1", 0));

        AdminWalletDto dto = service().getWallet("u1");

        assertThat(dto.balanceMinorUnits()).isZero();
        assertThat(dto.userId()).isEqualTo("u1");
    }

    @Test
    void listTransactionsValidatesTheUserExistsBeforeTouchingTheWallet() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().listTransactions("ghost", 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(walletService, never()).getOrCreateWallet(any());
    }

    @Test
    void freezingAnActiveWalletBlocksFutureCreditsAndDebits() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(walletService.resolveOrCreateWalletId("u1")).thenReturn("w1");
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(wallet("w1", "u1", 1500)));

        AdminWalletDto dto = service().setStatus("admin1", "u1",
                new SetWalletStatusRequest("FROZEN", "suspected fraud"), "127.0.0.1");

        assertThat(dto.status()).isEqualTo("FROZEN");
        verify(walletRepository).save(argThat(w -> w.getStatus() == WalletStatus.FROZEN));
        verify(auditService).log(eq("admin1"), any(), eq("Wallet"), eq("w1"), any(), any());
        verify(notificationService).notify(eq("u1"), eq(NotificationType.wallet), any(), any(), eq("w1"), eq("admin1"));
    }

    @Test
    void unfreezingAFrozenWalletRestoresIt() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(walletService.resolveOrCreateWalletId("u1")).thenReturn("w1");
        Wallet frozen = wallet("w1", "u1", 1500);
        frozen.setStatus(WalletStatus.FROZEN);
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(frozen));

        AdminWalletDto dto = service().setStatus("admin1", "u1", new SetWalletStatusRequest("ACTIVE", null), "127.0.0.1");

        assertThat(dto.status()).isEqualTo("ACTIVE");
    }

    @Test
    void freezingAnAlreadyFrozenWalletIsAConflict() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(walletService.resolveOrCreateWalletId("u1")).thenReturn("w1");
        Wallet frozen = wallet("w1", "u1", 1500);
        frozen.setStatus(WalletStatus.FROZEN);
        when(walletRepository.findByIdForUpdate("w1")).thenReturn(Optional.of(frozen));

        assertThatThrownBy(() -> service().setStatus("admin1", "u1", new SetWalletStatusRequest("FROZEN", null), "127.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void adminCannotFreezeTheirOwnWallet() {
        assertThatThrownBy(() -> service().setStatus("admin1", "admin1", new SetWalletStatusRequest("FROZEN", null), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void settingAnInvalidWalletStatusIsRejected() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));

        assertThatThrownBy(() -> service().setStatus("admin1", "u1", new SetWalletStatusRequest("CLOSED", null), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }
}
