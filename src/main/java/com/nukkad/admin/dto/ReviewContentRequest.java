package com.nukkad.admin.dto;

import jakarta.validation.constraints.Size;

/** Shared pre-publish approve/reject request body for Idea/Startup/Opportunity — see
 *  IdeaService.reviewModeration for the rationale behind reusing one shape across all three.
 *  {@code reason} is required by the service (not here) when {@code approved} is false. */
public record ReviewContentRequest(boolean approved, @Size(max = 500) String reason) {
}
