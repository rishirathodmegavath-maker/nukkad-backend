package com.nukkad.grant.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record UpdateGrantRequest(
        @Size(max = 200) String name,
        @Size(max = 200) String provider,
        String providerType,
        String description,
        String fundingAmount,
        String eligibilityCriteria,
        List<String> eligibleSectors,
        List<String> eligibleStages,
        Instant deadline,
        @Size(max = 500) String applicationUrl
) {
}
