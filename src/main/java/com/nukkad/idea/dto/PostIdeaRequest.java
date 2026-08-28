package com.nukkad.idea.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record PostIdeaRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String problem,
        @NotBlank String solution,
        @Size(max = 300) String targetCustomer,
        @NotNull String stage,
        @Size(max = 100) String category,
        Set<String> tags,
        Set<String> helpNeeded
) {
}
