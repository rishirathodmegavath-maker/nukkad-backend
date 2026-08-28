package com.nukkad.notification.dto;

import java.time.Instant;

public record NotificationDto(
        String id,
        String userId,
        String type,
        String title,
        String message,
        String relatedId,
        String actorUserId,
        boolean isRead,
        Instant createdAt
) {
}
