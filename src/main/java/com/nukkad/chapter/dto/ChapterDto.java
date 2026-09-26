package com.nukkad.chapter.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public record ChapterDto(
        String id,
        String name,
        String city,
        String country,
        String description,
        String coverImageUrl,
        String logoUrl,
        String presidentUserId,
        LocalDate foundedAt,
        String institution,
        String type,
        Set<String> focusAreas,
        long memberCount,
        long ideaCount,
        long startupCount,
        long opportunityCount,
        long eventCount,
        long resourceCount,
        long discussionCount,
        Instant createdAt,
        Instant updatedAt
) {
}
