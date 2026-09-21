package com.nukkad.startup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateStartupRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String logoUrl,
        @Size(max = 300) String tagline,
        @Size(max = 100) String sector,
        String problem,
        String solution,
        String stage,
        Set<@Size(max = 100, message = "each item must be 100 characters or fewer") String> needs,
        String chapterId
) {
}
