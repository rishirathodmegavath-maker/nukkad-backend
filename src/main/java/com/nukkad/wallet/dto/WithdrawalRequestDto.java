package com.nukkad.wallet.dto;

import java.time.Instant;

public record WithdrawalRequestDto(
        String id,
        long amountMinorUnits,
        String currency,
        String note,
        String status,
        String decisionNote,
        Instant createdAt,
        Instant decidedAt
) {
}
