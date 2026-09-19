package com.nukkad.opportunity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record PostOpportunityRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull String type,
        String startupId,
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
        Instant applicationDeadline
) {
}
