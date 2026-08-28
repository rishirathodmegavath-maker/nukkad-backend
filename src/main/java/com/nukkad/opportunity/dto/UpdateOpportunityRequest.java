package com.nukkad.opportunity.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateOpportunityRequest(
        @Size(max = 200) String title,
        String type,
        String startupId,
        @Size(max = 200) String organizationName,
        String location,
        Boolean remote,
        String description,
        List<String> requirements,
        String compensation
) {
}
