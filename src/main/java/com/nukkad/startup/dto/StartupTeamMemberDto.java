package com.nukkad.startup.dto;

import java.time.Instant;

public record StartupTeamMemberDto(
        String id,
        String startupId,
        String userId,
        String role,
        boolean isFounder,
        String status,
        String roleId,
        Instant createdAt,
        Instant reviewedAt
) {
}
