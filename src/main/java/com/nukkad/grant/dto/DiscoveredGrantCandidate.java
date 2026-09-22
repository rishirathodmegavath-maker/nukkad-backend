package com.nukkad.grant.dto;

import com.nukkad.grant.entity.GrantProviderType;
import com.nukkad.startup.entity.StartupStage;

import java.time.Instant;
import java.util.Set;

/**
 * A discovery candidate that has already passed com.nukkad.grant.discovery.GrantDiscoveryService's
 * validation -- a real provider-type label, real stage labels, a parseable and not-yet-expired
 * deadline, an absolute application URL and an absolute source URL. GrantService never sees the
 * AI's raw, untrusted JSON output directly; it only ever receives one of these.
 */
public record DiscoveredGrantCandidate(
        String name,
        String provider,
        GrantProviderType providerType,
        String description,
        String fundingAmount,
        String eligibilityCriteria,
        Set<String> eligibleSectors,
        Set<StartupStage> eligibleStages,
        Instant deadline,
        String applicationUrl,
        String sourceUrl
) {
}
