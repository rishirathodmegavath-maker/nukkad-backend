package com.nukkad.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpsertAchievementRequest(@NotBlank @Size(max = 200) String title,
                                        @Size(max = 200) String organization,
                                        LocalDate achievedOn,
                                        String description,
                                        @Size(max = 300) String credentialUrl) {
}
