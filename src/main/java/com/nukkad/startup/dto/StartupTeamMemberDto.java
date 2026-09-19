package com.nukkad.startup.dto;

import java.time.Instant;

public record StartupTeamMemberDto(
        String id,
        String startupId,
        String userId,
        String role,
        String teamRole,
        boolean isFounder,
        boolean isAdmin,
        boolean canManage,
        String status,
        String roleId,
        Instant createdAt,
        Instant reviewedAt
) {
}
