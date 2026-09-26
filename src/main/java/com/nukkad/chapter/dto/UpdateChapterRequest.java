package com.nukkad.chapter.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateChapterRequest(
        @Size(max = 150) String name,
        @Size(max = 100) String city,
        @Size(max = 100) String country,
        String description,
        @Size(max = 500) String coverImageUrl,
        @Size(max = 150) String institution,
        @Size(max = 50) String type,
        Set<@Size(max = 50) String> focusAreas
) {
}
