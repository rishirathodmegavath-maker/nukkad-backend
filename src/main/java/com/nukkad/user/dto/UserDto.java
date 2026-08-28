package com.nukkad.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record UserDto(
        String id,
        String name,
        String email,
        String avatarUrl,
        String coverUrl,
        String headline,
        String role,
        String collegeOrCompany,
        String location,
        int experienceYears,
        Set<String> skills,
        Set<String> lookingFor,
        Set<String> openTo,
        Map<String, String> socialLinks,
        String goals,
        String bio,
        String availability,
        String chapterId,
        int connectionsCount,
        boolean isOnline,
        Instant createdAt,
        String connectionStatus,
        Boolean isFollowing,
        List<ExperienceDto> experiences,
        List<EducationDto> education,
        List<AchievementDto> achievements,
        List<ProjectDto> projects,
        List<CertificationDto> certifications,
        List<PublicationDto> publications,
        Integer profileCompleteness,
        List<EndorsementSummaryDto> endorsementSummary,
        List<RecommendationDto> recommendations,
        Set<String> roles
) {
}
