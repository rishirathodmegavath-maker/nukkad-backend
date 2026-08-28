package com.nukkad.user.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;

public record UpdateUserRequest(
        @Size(min = 1, max = 120) String name,
        @Size(max = 500) String avatarUrl,
        @Size(max = 200) String headline,
        @Size(max = 150) String role,
        @Size(max = 200) String collegeOrCompany,
        @Size(max = 200) String location,
        Integer experienceYears,
        Set<String> skills,
        Set<String> lookingFor,
        Set<String> openTo,
        Map<String, String> socialLinks,
        String goals,
        String bio,
        String availability,
        String chapterId
) {
}
