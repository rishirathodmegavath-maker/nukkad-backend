package com.nukkad.investor.dto;

import java.time.Instant;

public record FundraiseDto(
        String id,
        String startupId,
        String startupName,
        long targetAmount,
        long amountRaised,
        String fundingStage,
        String useOfFunds,
        Long minimumTicket,
        String status,
        boolean canManage,
        Instant createdAt,
        Instant updatedAt
) {
}
