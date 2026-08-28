package com.nukkad.auth.dto;

import java.time.Instant;

public record SessionDto(
        String id,
        String deviceLabel,
        String ipAddress,
        Instant lastUsedAt,
        Instant createdAt,
        boolean isCurrent
) {
}
