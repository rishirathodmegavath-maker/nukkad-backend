package com.nukkad.grant.discovery;

import java.util.List;

/**
 * Raw shape of one JSON object the AI returns for a discovery batch -- every field here is
 * untrusted, unvalidated model output until GrantDiscoveryService turns it into a
 * com.nukkad.grant.dto.DiscoveredGrantCandidate (or rejects it). Field names mirror the JSON keys
 * given in GrantDiscoveryPromptBuilder's prompt exactly.
 */
public record DiscoveredGrantDto(
        String grantSchemeName,
        String provider,
        String providerType,
        String description,
        String fundingAmount,
        String applicationDeadline,
        String eligibilityCriteria,
        List<String> eligibleStages,
        List<String> eligibleSectors,
        String applicationUrl,
        String sourceUrl
) {
}
