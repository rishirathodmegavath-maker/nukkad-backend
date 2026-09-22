package com.nukkad.investor.dto;

import java.time.Instant;

public record InvestorImportIssueDto(
        String id,
        int rowNumber,
        String externalSourceId,
        String investorName,
        String severity,
        String message,
        Instant createdAt
) {
}
