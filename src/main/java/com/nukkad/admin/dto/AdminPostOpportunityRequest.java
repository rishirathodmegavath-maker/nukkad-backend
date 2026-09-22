package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * An admin posting an opportunity from the admin panel. Same fields as a member's own posting (see
 * PostOpportunityRequest), minus a startup attribution — an admin-authored posting is a platform-level
 * listing, not tied to a specific BuildAdda startup. {@code postedByEmail} is optional: with it, that
 * member is attributed as the poster and is told; without it, the admin's own account is.
 */
public record AdminPostOpportunityRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull String type,
        @NotBlank @Size(max = 200) String organizationName,
        String location,
        @NotNull String workMode,
        @NotBlank String description,
        String responsibilities,
        List<String> requirements,
        List<String> requiredSkills,
        String compensation,
        String equity,
        String experienceLevel,
        Instant applicationDeadline,
        @Size(max = 255) String postedByEmail
) {
}
