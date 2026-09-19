package com.nukkad.admin.dto;

import jakarta.validation.constraints.Size;

/** Shared moderation-toggle request body for Idea/Startup/Opportunity admin removal — see
 *  IdeaService.setRemovedByAdmin for the rationale behind reusing one shape across all three. */
public record SetContentRemovedRequest(boolean removed, @Size(max = 500) String reason) {
}
