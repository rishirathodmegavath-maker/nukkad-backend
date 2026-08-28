package com.nukkad.idea.dto;

import com.nukkad.user.dto.ExperienceDto;
import com.nukkad.user.dto.ProjectDto;
import com.nukkad.user.dto.UserDto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record IdeaInterestDto(
        String id,
        String ideaId,
        String ideaTitle,
        UserDto applicant,
        String status,
        Set<String> contributionAreas,
        String message,
        List<String> relevantSkills,
        List<ExperienceDto> relevantExperience,
        List<ProjectDto> relevantProjects,
        Instant createdAt,
        Instant reviewedAt
) {
}
