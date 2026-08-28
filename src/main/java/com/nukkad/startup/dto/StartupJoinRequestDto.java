package com.nukkad.startup.dto;

import com.nukkad.user.dto.UserDto;

import java.time.Instant;

public record StartupJoinRequestDto(
        String id,
        String startupId,
        String startupName,
        UserDto applicant,
        String status,
        String roleId,
        String roleTitle,
        String message,
        Instant createdAt,
        Instant reviewedAt
) {
}
