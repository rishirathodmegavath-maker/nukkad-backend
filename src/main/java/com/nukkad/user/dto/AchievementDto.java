package com.nukkad.user.dto;

import java.time.LocalDate;

public record AchievementDto(String id, String title, String organization, LocalDate achievedOn,
                              String description, String credentialUrl, int sortOrder) {
}
