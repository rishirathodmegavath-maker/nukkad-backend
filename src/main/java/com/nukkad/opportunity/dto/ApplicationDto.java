package com.nukkad.opportunity.dto;

import com.nukkad.user.dto.ExperienceDto;
import com.nukkad.user.dto.ProjectDto;
import com.nukkad.user.dto.UserDto;

import java.time.Instant;
import java.util.List;

public record ApplicationDto(
        String id,
        String opportunityId,
        String opportunityTitle,
        UserDto applicant,
        String status,
        String whyInterested,
        String whyGoodFit,
        List<String> relevantSkills,
        List<ExperienceDto> relevantExperience,
        List<ProjectDto> relevantProjects,
        String availability,
        String expectedCommitment,
        String additionalMessage,
        Instant createdAt,
        Instant reviewedAt
) {
}
