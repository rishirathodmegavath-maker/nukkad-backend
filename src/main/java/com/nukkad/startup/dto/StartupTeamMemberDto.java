package com.nukkad.startup.dto;

import com.nukkad.user.dto.UserDto;

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
        Instant reviewedAt,
        /** Who this is, as the viewer may see them. Filled in on the team list; null on single-row answers (join, role change). */
        UserDto user
) {
}
