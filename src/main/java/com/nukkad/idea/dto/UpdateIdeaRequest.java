package com.nukkad.idea.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateIdeaRequest(
        @Size(max = 200) String title,
        String problem,
        String solution,
        @Size(max = 300) String targetCustomer,
        String stage,
        @Size(max = 100) String category,
        Set<String> tags,
        Set<String> helpNeeded
) {
}
