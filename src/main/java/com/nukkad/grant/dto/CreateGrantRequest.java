package com.nukkad.grant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreateGrantRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String provider,
        @NotNull String providerType,
        String description,
        String fundingAmount,
        String eligibilityCriteria,
        List<String> eligibleSectors,
        List<String> eligibleStages,
        Instant deadline,
        @NotBlank @Size(max = 500) String applicationUrl
) {
}
