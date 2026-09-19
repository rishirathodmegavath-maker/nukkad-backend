package com.nukkad.admin.dto;

import java.time.Instant;

public record AdminWalletDto(
        String id,
        String userId,
        String userName,
        String userEmail,
        String currency,
        long balanceMinorUnits,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
