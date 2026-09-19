package com.nukkad.wallet.mapper;

import com.nukkad.wallet.dto.WalletDto;
import com.nukkad.wallet.dto.WalletTransactionDto;
import com.nukkad.wallet.dto.WithdrawalRequestDto;
import com.nukkad.wallet.entity.Wallet;
import com.nukkad.wallet.entity.WalletTransaction;
import com.nukkad.wallet.entity.WithdrawalRequest;
import org.springframework.stereotype.Component;

@Component
public class WalletMapper {

    public WalletDto toDto(Wallet wallet) {
        return new WalletDto(
                wallet.getId(),
                wallet.getCurrency(),
                wallet.getBalanceMinorUnits(),
                wallet.getStatus().name(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }

    public WalletTransactionDto toDto(WalletTransaction transaction) {
        return new WalletTransactionDto(
                transaction.getId(),
                transaction.getType().name(),
                transaction.getAmountMinorUnits(),
                transaction.getCurrency(),
                transaction.getStatus().name(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }

    public WithdrawalRequestDto toDto(WithdrawalRequest request) {
        return new WithdrawalRequestDto(
                request.getId(),
                request.getAmountMinorUnits(),
                request.getCurrency(),
                request.getNote(),
                request.getStatus().name(),
                request.getDecisionNote(),
                request.getCreatedAt(),
                request.getDecidedAt()
        );
    }
}
