package com.nukkad.wallet.dto;

import java.time.Instant;

public record WalletDto(
        String id,
        String currency,
        long balanceMinorUnits,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
