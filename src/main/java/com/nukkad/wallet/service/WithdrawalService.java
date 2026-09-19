package com.nukkad.wallet.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.wallet.entity.WithdrawalRequest;
import com.nukkad.wallet.entity.WithdrawalStatus;
import com.nukkad.wallet.repository.WithdrawalRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User-facing half of the withdrawal flow — see {@link WithdrawalRequest}'s class comment for how
 * this fits with {@link WalletService}'s append-only ledger. The admin-facing approve/reject half
 * lives in {@code AdminWithdrawalService}, which reuses {@link #refund} for its own rejection path
 * so there is exactly one place funds are ever returned to a wallet after a hold.
 */
@Service
public class WithdrawalService {

    private final WalletService walletService;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final AuditService auditService;

    public WithdrawalService(WalletService walletService, WithdrawalRequestRepository withdrawalRequestRepository,
                              AuditService auditService) {
        this.walletService = walletService;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.auditService = auditService;
    }

    public WithdrawalRequest getEntityOrThrow(String id) {
        return withdrawalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal request not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<WithdrawalRequest> listMine(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        return withdrawalRequestRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Holds the requested amount immediately (via {@link WalletService#debit}) so the same balance
     * can't be withdrawn twice while a decision is pending — see the class-level rationale on
     * {@link WithdrawalRequest}. Saved twice: once to obtain an id to use as the debit's
     * {@code referenceId}, again once the resulting hold transaction id is known.
     */
    @Transactional
    public WithdrawalRequest request(String userId, long amountMinorUnits, String note) {
        String walletId = walletService.resolveOrCreateWalletId(userId);

        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .userId(userId)
                .walletId(walletId)
                .amountMinorUnits(amountMinorUnits)
                .currency(WalletService.DEFAULT_CURRENCY)
                .note(note)
                .status(WithdrawalStatus.PENDING)
                .build();
        withdrawal = withdrawalRequestRepository.saveAndFlush(withdrawal);

        var hold = walletService.debit(walletId, amountMinorUnits, "WITHDRAWAL_HOLD", withdrawal.getId(),
                withdrawal.getId(), "Withdrawal request");
        withdrawal.setHoldTransactionId(hold.getId());
        withdrawal = withdrawalRequestRepository.saveAndFlush(withdrawal);

        auditService.log(userId, AuditAction.WALLET_WITHDRAWAL_REQUESTED, "WithdrawalRequest", withdrawal.getId(), null);
        return withdrawal;
    }

    @Transactional
    public WithdrawalRequest cancel(String userId, String requestId) {
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal request not found: " + requestId));
        if (!withdrawal.getUserId().equals(userId)) {
            throw new ForbiddenException("You can only cancel your own withdrawal request");
        }
        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new ConflictException("This withdrawal request has already been decided");
        }
        return refund(withdrawal, WithdrawalStatus.CANCELLED, null, null);
    }

    /** Shared by {@code cancel} above and {@code AdminWithdrawalService#reject} — the one place a
     *  hold is ever returned to the wallet. Caller must already hold the row lock on {@code withdrawal}
     *  (via {@link WithdrawalRequestRepository#findByIdForUpdate}) and must have already verified its
     *  status is still {@code PENDING} — this method does not re-check either. */
    @Transactional
    public WithdrawalRequest refund(WithdrawalRequest withdrawal, WithdrawalStatus newStatus, String decidedByAdminId, String decisionNote) {
        if (withdrawal.getAmountMinorUnits() <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        var refundTxn = walletService.credit(withdrawal.getWalletId(), withdrawal.getAmountMinorUnits(),
                "WITHDRAWAL_REFUND", withdrawal.getId(), withdrawal.getId() + ":refund",
                decisionNote != null ? "Withdrawal request " + newStatus.name().toLowerCase() + ": " + decisionNote
                        : "Withdrawal request " + newStatus.name().toLowerCase());
        withdrawal.setStatus(newStatus);
        withdrawal.setRefundTransactionId(refundTxn.getId());
        withdrawal.setDecisionNote(decisionNote);
        withdrawal.setDecidedByAdminId(decidedByAdminId);
        withdrawal.setDecidedAt(java.time.Instant.now());
        return withdrawalRequestRepository.saveAndFlush(withdrawal);
    }
}
