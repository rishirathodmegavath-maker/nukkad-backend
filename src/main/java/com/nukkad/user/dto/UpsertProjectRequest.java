package com.nukkad.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record UpsertProjectRequest(@NotBlank @Size(max = 200) String title,
                                    String description,
                                    List<String> technologies,
                                    @Size(max = 500) String imageUrl,
                                    @Size(max = 300) String githubUrl,
                                    @Size(max = 300) String liveUrl,
                                    LocalDate startDate,
                                    LocalDate endDate,
                                    String projectType) {
}
