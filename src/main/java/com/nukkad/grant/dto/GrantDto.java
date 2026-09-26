package com.nukkad.grant.dto;

import java.time.Instant;
import java.util.List;

public record GrantDto(
        String id,
        String name,
        String provider,
        String providerType,
        String description,
        String fundingAmount,
        String eligibilityCriteria,
        List<String> eligibleSectors,
        List<String> eligibleStages,
        Instant deadline,
        String applicationUrl,
        String sourceUrl,
        String discoveryOrigin,
        Instant lastVerifiedAt,
        String createdByUserId,
        boolean postedAsPlatform,
        String publisherIdentity,
        boolean removedByAdmin,
        String removalReason,
        String moderationStatus,
        String rejectionReason,
        boolean canManage,
        Instant createdAt,
        Instant updatedAt
) {
}
