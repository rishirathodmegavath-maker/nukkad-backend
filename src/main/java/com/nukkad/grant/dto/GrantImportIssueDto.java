package com.nukkad.grant.dto;

import java.time.Instant;

public record GrantImportIssueDto(
        String id,
        int rowNumber,
        String grantName,
        String severity,
        String message,
        Instant createdAt
) {
}
