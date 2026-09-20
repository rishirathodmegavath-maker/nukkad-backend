package com.nukkad.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Exactly one of {@code reportedUserId} or {@code postId} is required — reporting a post
 *  resolves {@code reportedUserId} server-side from the post's own author (see
 *  ReportService.submit), so the client never has to supply both and a forged mismatch between
 *  them is impossible. */
public record SubmitReportRequest(
        String reportedUserId,
        // Matches the reports.category column (VARCHAR(60)) so an over-length value is a clean
        // validation error instead of a DB "Data truncation" exception.
        @NotBlank @Size(max = 60) String category,
        String conversationId,
        String postId
) {
}
