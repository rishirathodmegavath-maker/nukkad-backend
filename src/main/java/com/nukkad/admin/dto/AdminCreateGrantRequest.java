package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * An admin publishing a grant listing from the admin panel. Same fields as a member's own submission
 * (see CreateGrantRequest). {@code createdByEmail} is optional: with it, that member is attributed as
 * its creator and is told; without it, the admin's own account is, and {@code publisherIdentity} picks
 * which curator identity to display instead — ignored when createdByEmail is set. Never the grant
 * provider ({@code provider}, a government body or VC) — this only credits a BuildAdda curator.
 */
public record AdminCreateGrantRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String provider,
        @NotNull String providerType,
        String description,
        String fundingAmount,
        String eligibilityCriteria,
        List<String> eligibleSectors,
        List<String> eligibleStages,
        Instant deadline,
        @NotBlank @Size(max = 500) String applicationUrl,
        @Size(max = 255) String createdByEmail,
        String publisherIdentity
) {
}
