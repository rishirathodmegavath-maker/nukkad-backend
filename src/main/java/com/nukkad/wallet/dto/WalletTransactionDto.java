package com.nukkad.wallet.dto;

import java.time.Instant;

public record WalletTransactionDto(
        String id,
        String type,
        long amountMinorUnits,
        String currency,
        String status,
        String referenceType,
        String referenceId,
        String description,
        Instant createdAt,
        Instant completedAt
) {
}
