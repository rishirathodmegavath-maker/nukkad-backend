package com.nukkad.report.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitReportRequest(@NotBlank String reportedUserId, @NotBlank String category, String conversationId) {
}
