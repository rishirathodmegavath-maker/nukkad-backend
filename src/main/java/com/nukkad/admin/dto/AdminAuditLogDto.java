package com.nukkad.admin.dto;

import java.time.Instant;

public record AdminAuditLogDto(
        String id,
        String actorId,
        String actorName,
        String action,
        String entityType,
        String entityId,
        String details,
        String ipAddress,
        Instant createdAt
) {
}
