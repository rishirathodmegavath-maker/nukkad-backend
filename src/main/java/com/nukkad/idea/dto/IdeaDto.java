package com.nukkad.idea.dto;

import java.time.Instant;
import java.util.Set;

public record IdeaDto(
        String id,
        String title,
        String problem,
        String solution,
        String targetCustomer,
        String stage,
        String category,
        String creatorId,
        String chapterId,
        String startupId,
        Set<String> tags,
        Set<String> helpNeeded,
        Set<String> teamMemberIds,
        int interestCount,
        Instant createdAt,
        Instant updatedAt
) {
}
