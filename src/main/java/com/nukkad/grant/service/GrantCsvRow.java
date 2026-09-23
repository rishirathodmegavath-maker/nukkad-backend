package com.nukkad.grant.service;

import com.nukkad.grant.entity.GrantProviderType;
import com.nukkad.startup.entity.StartupStage;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** One parsed, validated row from an admin-uploaded grants spreadsheet — see {@link GrantCsvParser}. */
public record GrantCsvRow(
        int rowNumber,
        String name,
        String provider,
        String rawProviderType,
        GrantProviderType resolvedProviderType,
        String description,
        String fundingAmount,
        String eligibilityCriteria,
        Set<String> eligibleSectors,
        Set<StartupStage> eligibleStages,
        String rawDeadline,
        Instant deadline,
        String applicationUrl,
        List<String> warnings,
        String hardError
) {
    public boolean hasHardError() {
        return hardError != null;
    }
}
