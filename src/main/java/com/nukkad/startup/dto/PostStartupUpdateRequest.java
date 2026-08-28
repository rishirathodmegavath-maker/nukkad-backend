package com.nukkad.startup.dto;

import jakarta.validation.constraints.NotBlank;

public record PostStartupUpdateRequest(@NotBlank String content) {
}
