package com.nukkad.resource.dto;

import java.time.Instant;
import java.util.Set;

public record ResourceDto(
        String id,
        String title,
        String description,
        String type,
        String url,
        String uploaderUserId,
        String chapterId,
        String chapterName,
        Set<String> tags,
        boolean isSaved,
        boolean canManage,
        Instant createdAt,
        Instant updatedAt
) {
}
