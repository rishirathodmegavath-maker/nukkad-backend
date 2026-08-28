package com.nukkad.opportunity.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Set;

public record ApplyToOpportunityRequest(
        @NotBlank String whyInterested,
        @NotBlank String whyGoodFit,
        Set<String> relevantSkills,
        List<String> experienceIds,
        List<String> projectIds,
        String availability,
        String expectedCommitment,
        String additionalMessage
) {
}
