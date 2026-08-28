package com.nukkad.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WriteRecommendationRequest(@Size(max = 100) String relationship, @NotBlank String body) {
}
