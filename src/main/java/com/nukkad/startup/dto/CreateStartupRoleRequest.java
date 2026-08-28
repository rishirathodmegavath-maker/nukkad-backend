package com.nukkad.startup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStartupRoleRequest(
        @NotBlank String title,
        @NotNull String type,
        String location,
        boolean remote
) {
}
