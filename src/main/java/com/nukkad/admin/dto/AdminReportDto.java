package com.nukkad.admin.dto;

import java.time.Instant;

public record AdminReportDto(
        String id,
        String reporterId,
        String reporterName,
        String reportedUserId,
        String reportedUserName,
        String category,
        String conversationId,
        String postId,
        String status,
        Instant createdAt,
        String resolvedByUserId,
        String resolvedByName,
        Instant resolvedAt,
        String resolutionNote
) {
}
