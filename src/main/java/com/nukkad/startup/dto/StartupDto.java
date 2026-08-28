package com.nukkad.startup.dto;

import java.time.Instant;
import java.util.Set;

public record StartupDto(
        String id,
        String name,
        String logoUrl,
        String tagline,
        String sector,
        String problem,
        String solution,
        String stage,
        String traction,
        String ideaId,
        String chapterId,
        boolean isRaising,
        Set<String> needs,
        boolean isFollowing,
        Instant createdAt,
        Instant updatedAt
) {
}
