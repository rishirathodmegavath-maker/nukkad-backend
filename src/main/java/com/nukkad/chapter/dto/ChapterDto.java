package com.nukkad.chapter.dto;

import java.time.Instant;
import java.util.Set;

public record ChapterDto(
        String id,
        String name,
        String city,
        String country,
        String description,
        String coverImageUrl,
        String presidentUserId,
        String institution,
        String type,
        Set<String> focusAreas,
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
