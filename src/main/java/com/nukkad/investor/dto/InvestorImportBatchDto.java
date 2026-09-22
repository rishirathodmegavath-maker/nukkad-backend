package com.nukkad.investor.dto;

import java.time.Instant;

/** Polled by the admin UI while a CSV import runs, and shown as the final report once it finishes. */
public record InvestorImportBatchDto(
        String id,
        String originalFilename,
        String status,
        int totalRows,
        int processedRows,
        int createdCount,
        int updatedCount,
        int skippedCount,
        int failedCount,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {
}
