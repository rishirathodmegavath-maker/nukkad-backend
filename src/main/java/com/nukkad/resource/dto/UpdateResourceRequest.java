package com.nukkad.resource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateResourceRequest(
        @Size(max = 200) String title,
        String description,
        String type,
        /** Null means "don't change"; blank string means "take it off its shelf". */
        String category,
        /** Null means "don't change"; blank string clears it. */
        @Size(max = 120) String provider,
        /** Null means "don't change"; 0 clears it. */
        @Min(0) @Max(6000) Integer durationMinutes,
        /** Null means "don't change". */
        Boolean featured,
        @Size(max = 500) String url,
        /** Null means "don't change"; blank string means "unassign from any chapter". */
        String chapterId,
        Set<String> tags
) {
}
