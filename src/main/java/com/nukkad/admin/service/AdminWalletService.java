package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdjustWalletBalanceRequest;
import com.nukkad.admin.dto.AdminWalletDto;
import com.nukkad.admin.dto.SetWalletStatusRequest;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.wallet.dto.WalletTransactionDto;
import com.nukkad.wallet.entity.Wallet;
import com.nukkad.wallet.entity.WalletStatus;
import com.nukkad.wallet.mapper.WalletMapper;
import com.nukkad.wallet.repository.WalletRepository;
import com.nukkad.wallet.service.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only administrative path that can move money. There is no "set balance" operation — every
 * adjustment goes through {@link WalletService#credit}/{@link WalletService#debit}, the exact
 * same ledger-writing, row-locked, idempotent path any future business flow will use — so an
 * admin adjustment can never silently rewrite financial history, only add a new, reasoned entry
 * to it.
 */
@Service
public class AdminWalletService {

    private final WalletService walletService;
    private final WalletMapper walletMapper;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public AdminWalletService(WalletService walletService, WalletMapper walletMapper, WalletRepository walletRepository,
                               UserRepository userRepository, AuditService auditService,
                               NotificationService notificationService) {
        this.walletService = walletService;
        this.walletMapper = walletMapper;
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public AdminWalletDto getWallet(String targetUserId) {
        User user = requireUser(targetUserId);
        Wallet wallet = walletService.getOrCreateWallet(targetUserId);
        return toDto(wallet, user);
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionDto> listTransactions(String targetUserId, int page, int size) {
        requireUser(targetUserId);
        Wallet wallet = walletService.getOrCreateWallet(targetUserId);
        return walletService.listTransactions(wallet.getId(), page, AdminPaging.clampSize(size)).map(walletMapper::toDto);
    }

    @Transactional
    public AdminWalletDto adjustBalance(String adminId, String targetUserId, AdjustWalletBalanceRequest request, String ip) {
        if (adminId.equals(targetUserId)) {
            throw new BadRequestException("Admins cannot adjust their own wallet balance");
        }
        User user = requireUser(targetUserId);

        String direction = request.direction() == null ? "" : request.direction().trim().toUpperCase();
        if (!direction.equals("CREDIT") && !direction.equals("DEBIT")) {
            throw new BadRequestException("direction must be CREDIT or DEBIT");
        }
        String currency = (request.currency() == null || request.currency().isBlank())
                ? WalletService.DEFAULT_CURRENCY
                : request.currency().trim().toUpperCase();
        if (!currency.equals(WalletService.DEFAULT_CURRENCY)) {
            throw new BadRequestException("Unsupported currency: " + currency);
        }

        // Resolves via a bare-id projection, NOT a loaded entity -- loading the Wallet here first
        // and then requesting a locked read on the same id inside credit()/debit() within this
        // same transaction would let Hibernate satisfy the lock request from its already-cached,
        // unlocked copy instead of re-acquiring the row lock, silently defeating the very
        // protection that prevents two concurrent adjustments from losing one of their updates.
        String walletId = walletService.resolveOrCreateWalletId(targetUserId);
        if (direction.equals("CREDIT")) {
            walletService.credit(walletId, request.amountMinorUnits(), "ADMIN_ADJUSTMENT", adminId,
                    request.idempotencyKey(), request.reason());
        } else {
            walletService.debit(walletId, request.amountMinorUnits(), "ADMIN_ADJUSTMENT", adminId,
                    request.idempotencyKey(), request.reason());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("targetUserId", targetUserId);
        details.put("direction", direction);
        details.put("amountMinorUnits", request.amountMinorUnits());
        details.put("currency", currency);
        details.put("reason", request.reason());
        auditService.log(adminId, AuditAction.WALLET_ADMIN_ADJUSTMENT, "Wallet", walletId, ip, details);

        notificationService.notify(targetUserId, NotificationType.wallet,
                direction.equals("CREDIT") ? "Wallet credited" : "Wallet debited",
                "An administrator has " + (direction.equals("CREDIT") ? "credited" : "debited")
                        + " your wallet: " + request.reason(),
                walletId, adminId);

        Wallet refreshed = walletService.getWalletOrThrow(targetUserId);
        return toDto(refreshed, user);
    }

    /**
     * Freezes or unfreezes a wallet. Freezing blocks every future credit/debit at the source
     * ({@link WalletService#applyLedgerEntry} rejects a non-ACTIVE wallet) — it does not touch the
     * balance or any existing ledger row. Locks the row (mirroring the reasoning in
     * {@link #adjustBalance}) so this can never race a concurrent credit/debit into an
     * inconsistent status transition.
     */
    @Transactional
    public AdminWalletDto setStatus(String adminId, String targetUserId, SetWalletStatusRequest request, String ip) {
        if (adminId.equals(targetUserId)) {
            throw new BadRequestException("Admins cannot freeze or unfreeze their own wallet");
        }
        User user = requireUser(targetUserId);
        WalletStatus newStatus = parseStatus(request.status());

        String walletId = walletService.resolveOrCreateWalletId(targetUserId);
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        WalletStatus oldStatus = wallet.getStatus();
        if (oldStatus == newStatus) {
            throw new ConflictException("This wallet is already " + newStatus.name().toLowerCase());
        }
        wallet.setStatus(newStatus);
        walletRepository.save(wallet);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("targetUserId", targetUserId);
        details.put("oldStatus", oldStatus.name());
        details.put("newStatus", newStatus.name());
        if (request.reason() != null && !request.reason().isBlank()) {
            details.put("reason", request.reason());
        }
        auditService.log(adminId, AuditAction.WALLET_STATUS_CHANGED, "Wallet", walletId, ip, details);

        notificationService.notify(targetUserId, NotificationType.wallet,
                newStatus == WalletStatus.FROZEN ? "Wallet frozen" : "Wallet unfrozen",
                newStatus == WalletStatus.FROZEN
                        ? "An administrator has frozen your wallet."
                                + (request.reason() != null && !request.reason().isBlank() ? " Reason: " + request.reason() : "")
                        : "An administrator has unfrozen your wallet — it is active again.",
                walletId, adminId);

        return toDto(wallet, user);
    }

    private WalletStatus parseStatus(String value) {
        try {
            return WalletStatus.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + value);
        }
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private AdminWalletDto toDto(Wallet wallet, User user) {
        return new AdminWalletDto(
                wallet.getId(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                wallet.getCurrency(),
                wallet.getBalanceMinorUnits(),
                wallet.getStatus().name(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }
}
