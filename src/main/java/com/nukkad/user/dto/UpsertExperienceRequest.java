package com.nukkad.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpsertExperienceRequest(@NotBlank @Size(max = 200) String company,
                                       @NotBlank @Size(max = 150) String role,
                                       @Size(max = 50) String employmentType,
                                       @Size(max = 200) String location,
                                       @NotNull LocalDate startDate,
                                       LocalDate endDate,
                                       boolean isCurrent,
                                       String description,
                                       @Size(max = 300) String companyUrl) {
}
