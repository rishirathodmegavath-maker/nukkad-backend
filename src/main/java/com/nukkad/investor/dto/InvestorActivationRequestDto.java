package com.nukkad.investor.dto;

import java.time.Instant;
import java.util.Set;

public record InvestorActivationRequestDto(
        String id,
        String status,
        String investorType,
        String firmName,
        String thesis,
        Set<String> sectors,
        Set<String> stages,
        Set<String> geographies,
        Long ticketMin,
        Long ticketMax,
        int portfolioCount,
        String website,
        String resultingProfileId,
        String reviewNote,
        Instant createdAt,
        Instant reviewedAt
) {
}
