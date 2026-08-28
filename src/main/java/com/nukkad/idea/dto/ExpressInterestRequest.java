package com.nukkad.idea.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Set;

public record ExpressInterestRequest(
        @NotEmpty Set<String> contributionAreas,
        String message,
        Set<String> relevantSkills,
        List<String> experienceIds,
        List<String> projectIds
) {
}
