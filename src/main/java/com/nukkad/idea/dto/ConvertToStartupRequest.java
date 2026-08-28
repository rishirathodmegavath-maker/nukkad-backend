package com.nukkad.idea.dto;

import jakarta.validation.constraints.Size;

public record ConvertToStartupRequest(
        @Size(max = 200) String name,
        @Size(max = 300) String tagline,
        @Size(max = 100) String sector
) {
}
