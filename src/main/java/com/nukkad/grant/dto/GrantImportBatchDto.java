package com.nukkad.grant.dto;

import java.time.Instant;

/** Polled by the admin UI while a spreadsheet import runs, and shown as the final report once it finishes. */
public record GrantImportBatchDto(
        String id,
        String originalFilename,
        String status,
        int totalRows,
        int processedRows,
        int createdCount,
        int skippedCount,
        int failedCount,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {
}
