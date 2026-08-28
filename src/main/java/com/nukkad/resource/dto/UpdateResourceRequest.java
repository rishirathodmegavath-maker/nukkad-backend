package com.nukkad.resource.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateResourceRequest(
        @Size(max = 200) String title,
        String description,
        String type,
        @Size(max = 500) String url,
        /** Null means "don't change"; blank string means "unassign from any chapter". */
        String chapterId,
        Set<String> tags
) {
}
