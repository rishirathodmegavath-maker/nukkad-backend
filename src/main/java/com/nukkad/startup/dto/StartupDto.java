package com.nukkad.startup.dto;

import java.time.Instant;
import java.util.Set;

public record StartupDto(
        String id,
        String name,
        String logoUrl,
        String location,
        String website,
        String tagline,
        String sector,
        String problem,
        String solution,
        String targetCustomer,
        String businessModel,
        String whatBuilding,
        String stage,
        String traction,
        String revenue,
        String customers,
        String users,
        String growth,
        String otherTraction,
        String keywords,
        String visibility,
        boolean fundraisingVisible,
        String ideaId,
        String chapterId,
        boolean isRaising,
        Set<String> needs,
        boolean isFollowing,
        boolean canManage,
        int profileCompletionPercent,
        boolean removedByAdmin,
        String removalReason,
        String moderationStatus,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
}
