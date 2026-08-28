package com.nukkad.user.dto;

import java.time.Instant;

public record RecommendationDto(String id, String authorUserId, String authorName, String authorAvatarUrl,
                                 String authorHeadline, String relationship, String body, String status,
                                 Instant createdAt, Instant respondedAt) {
}
