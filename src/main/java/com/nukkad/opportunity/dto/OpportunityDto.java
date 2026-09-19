package com.nukkad.opportunity.dto;

import java.time.Instant;
import java.util.List;

public record OpportunityDto(
        String id,
        String title,
        String type,
        boolean closed,
        boolean removedByAdmin,
        String removalReason,
        String moderationStatus,
        String rejectionReason,
        String startupId,
        String organizationName,
        String location,
        String workMode,
        String description,
        String responsibilities,
        String compensation,
        String equity,
        String experienceLevel,
        Instant applicationDeadline,
        String postedByUserId,
        String chapterId,
        List<String> requirements,
        List<String> requiredSkills,
        boolean hasApplied,
        boolean hasExpressedInterest,
        String applicationStatus,
        int applicantCount,
        int interestCount,
        Instant appliedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
