package com.nukkad.investor.dto;

import com.nukkad.user.dto.UserDto;

import java.time.Instant;

public record IntroRequestDto(
        String id,
        String requesterId,
        UserDto requester,
        String recipientId,
        UserDto recipient,
        String direction,
        String startupId,
        String startupName,
        String ideaId,
        String ideaTitle,
        String message,
        String status,
        Instant createdAt,
        Instant reviewedAt
) {
}
