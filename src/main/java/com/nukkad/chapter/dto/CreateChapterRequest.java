package com.nukkad.chapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChapterRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String city,
        @Size(max = 100) String country,
        String description,
        @Size(max = 500) String coverImageUrl
) {
}
