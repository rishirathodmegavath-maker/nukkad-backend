package com.nukkad.user.dto;

import java.time.LocalDate;
import java.util.List;

public record ProjectDto(String id, String title, String description, List<String> technologies,
                          String imageUrl, String githubUrl, String liveUrl, LocalDate startDate,
                          LocalDate endDate, String projectType, int sortOrder) {
}
