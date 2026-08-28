package com.nukkad.opportunity.dto;

import java.time.Instant;
import java.util.List;

public record OpportunityDto(
        String id,
        String title,
        String type,
        String startupId,
        String organizationName,
        String location,
        boolean remote,
        String description,
        String compensation,
        String postedByUserId,
        String chapterId,
        List<String> requirements,
        boolean hasApplied,
        boolean hasExpressedInterest,
        String applicationStatus,
        int applicantCount,
        int interestCount,
        Instant createdAt,
        Instant updatedAt
) {
}
