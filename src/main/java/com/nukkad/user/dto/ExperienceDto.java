package com.nukkad.user.dto;

import java.time.LocalDate;

public record ExperienceDto(String id, String company, String role, String employmentType, String location,
                             LocalDate startDate, LocalDate endDate, boolean isCurrent, String description,
                             String companyUrl, int sortOrder) {
}
