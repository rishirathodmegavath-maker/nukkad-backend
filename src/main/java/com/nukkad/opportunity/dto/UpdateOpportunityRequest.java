package com.nukkad.opportunity.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record UpdateOpportunityRequest(
        @Size(max = 200) String title,
        String type,
        String startupId,
        @Size(max = 200) String organizationName,
        String location,
        String workMode,
        String description,
        String responsibilities,
        List<String> requirements,
        List<String> requiredSkills,
        String compensation,
        String equity,
        String experienceLevel,
        Instant applicationDeadline
) {
}
