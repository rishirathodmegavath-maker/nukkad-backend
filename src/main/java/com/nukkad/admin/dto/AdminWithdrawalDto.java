package com.nukkad.admin.dto;

import java.time.Instant;

public record AdminWithdrawalDto(
        String id,
        String userId,
        String userName,
        String userEmail,
        long amountMinorUnits,
        String currency,
        String note,
        String status,
        String decisionNote,
        String decidedByAdminId,
        Instant createdAt,
        Instant decidedAt
) {
}
