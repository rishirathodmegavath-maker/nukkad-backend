package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminWithdrawalDto;
import com.nukkad.admin.mapper.AdminMapper;
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
import com.nukkad.wallet.entity.WithdrawalRequest;
import com.nukkad.wallet.entity.WithdrawalStatus;
import com.nukkad.wallet.repository.WithdrawalRequestRepository;
import com.nukkad.wallet.service.WithdrawalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Admin-facing half of the withdrawal flow — see {@link WithdrawalRequest}'s class comment. Reuses
 * {@link WithdrawalService#refund} for rejection so there is exactly one place funds are ever
 * returned to a wallet after a hold, matching how {@code AdminWalletService} reuses
 * {@code WalletService#credit}/{@code #debit} rather than mutating a balance itself.
 */
@Service
public class AdminWithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WithdrawalService withdrawalService;
    private final UserRepository userRepository;
    private final AdminMapper adminMapper;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public AdminWithdrawalService(WithdrawalRequestRepository withdrawalRequestRepository, WithdrawalService withdrawalService,
                                   UserRepository userRepository, AdminMapper adminMapper, AuditService auditService,
                                   NotificationService notificationService) {
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.withdrawalService = withdrawalService;
        this.userRepository = userRepository;
        this.adminMapper = adminMapper;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Page<AdminWithdrawalDto> list(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, AdminPaging.clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WithdrawalRequest> requests = parseStatus(status)
                .map(s -> withdrawalRequestRepository.findByStatusOrderByCreatedAtDesc(s, pageable))
                .orElseGet(() -> withdrawalRequestRepository.findAllByOrderByCreatedAtDesc(pageable));
        return requests.map(r -> adminMapper.toDto(r, userRepository.findById(r.getUserId()).orElse(null)));
    }

    @Transactional(readOnly = true)
    public AdminWithdrawalDto get(String id) {
        WithdrawalRequest request = getEntityOrThrow(id);
        User user = userRepository.findById(request.getUserId()).orElse(null);
        return adminMapper.toDto(request, user);
    }

    /** Finalizes the hold — no further ledger action, the funds already moved when the user
     *  requested the withdrawal; the actual payout happens manually outside the app. */
    @Transactional
    public AdminWithdrawalDto approve(String adminId, String requestId, String ip) {
        WithdrawalRequest request = withdrawalRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal request not found: " + requestId));
        requireDecidable(adminId, request);

        request.setStatus(WithdrawalStatus.APPROVED);
        request.setDecidedByAdminId(adminId);
        request.setDecidedAt(Instant.now());
        request = withdrawalRequestRepository.saveAndFlush(request);

        auditService.log(adminId, AuditAction.WALLET_WITHDRAWAL_APPROVED, "WithdrawalRequest", requestId, ip);
        notificationService.notify(request.getUserId(), NotificationType.wallet, "Withdrawal approved",
                "Your withdrawal request has been approved and will be processed.", requestId, adminId);

        return adminMapper.toDto(request, userRepository.findById(request.getUserId()).orElse(null));
    }

    @Transactional
    public AdminWithdrawalDto reject(String adminId, String requestId, String reason, String ip) {
        WithdrawalRequest request = withdrawalRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal request not found: " + requestId));
        requireDecidable(adminId, request);

        WithdrawalRequest rejected = withdrawalService.refund(request, WithdrawalStatus.REJECTED, adminId, reason);

        auditService.log(adminId, AuditAction.WALLET_WITHDRAWAL_REJECTED, "WithdrawalRequest", requestId, ip,
                java.util.Map.of("reason", reason));
        notificationService.notify(rejected.getUserId(), NotificationType.wallet, "Withdrawal rejected",
                "Your withdrawal request was not approved: " + reason, requestId, adminId);

        return adminMapper.toDto(rejected, userRepository.findById(rejected.getUserId()).orElse(null));
    }

    private void requireDecidable(String adminId, WithdrawalRequest request) {
        if (adminId.equals(request.getUserId())) {
            throw new BadRequestException("Admins cannot approve or reject their own withdrawal request");
        }
        if (request.getStatus() != WithdrawalStatus.PENDING) {
            throw new ConflictException("This withdrawal request has already been decided");
        }
    }

    private WithdrawalRequest getEntityOrThrow(String id) {
        return withdrawalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal request not found: " + id));
    }

    private java.util.Optional<WithdrawalStatus> parseStatus(String status) {
        if (status == null || status.isBlank()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(WithdrawalStatus.valueOf(status.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
