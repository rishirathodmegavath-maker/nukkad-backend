package com.nukkad.startup.dto;

import java.time.Instant;

public record StartupMaterialDto(
        String id,
        String startupId,
        String materialType,
        String title,
        String url,
        String originalFileName,
        String contentType,
        int sortOrder,
        boolean canManage,
        Instant createdAt,
        Instant updatedAt
) {
}
