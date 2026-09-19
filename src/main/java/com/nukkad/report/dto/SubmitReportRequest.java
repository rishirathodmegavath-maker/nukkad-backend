package com.nukkad.report.dto;

import jakarta.validation.constraints.NotBlank;

/** Exactly one of {@code reportedUserId} or {@code postId} is required — reporting a post
 *  resolves {@code reportedUserId} server-side from the post's own author (see
 *  ReportService.submit), so the client never has to supply both and a forged mismatch between
 *  them is impossible. */
public record SubmitReportRequest(String reportedUserId, @NotBlank String category, String conversationId, String postId) {
}
