package com.nukkad.chapter.dto;

import java.time.Instant;

public record ChapterDto(
        String id,
        String name,
        String city,
        String country,
        String description,
        String coverImageUrl,
        String presidentUserId,
        long memberCount,
        long ideaCount,
        long startupCount,
        long opportunityCount,
        long eventCount,
        long resourceCount,
        Instant createdAt,
        Instant updatedAt
) {
}
