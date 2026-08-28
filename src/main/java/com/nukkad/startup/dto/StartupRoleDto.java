package com.nukkad.startup.dto;

import java.time.Instant;

public record StartupRoleDto(
        String id,
        String startupId,
        String title,
        String type,
        String location,
        boolean remote,
        Instant createdAt
) {
}
