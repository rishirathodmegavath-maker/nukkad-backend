package com.nukkad.admin.dto;

import java.time.Instant;
import java.util.Set;

public record AdminInvestorActivationDto(
        String id,
        String requesterUserId,
        String requesterName,
        String requesterEmail,
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
        String reviewedBy,
        Instant createdAt,
        Instant reviewedAt
) {
}
