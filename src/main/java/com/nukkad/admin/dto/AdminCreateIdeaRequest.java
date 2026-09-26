package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * An admin publishing an idea from the admin panel. Same fields as a member's own idea (see
 * PostIdeaRequest). {@code creatorEmail} is optional: with it, that member is attributed as its
 * creator and is told; without it, the admin's own account is, and {@code publisherIdentity} picks
 * which platform identity to display instead — ignored when creatorEmail is set. Unlike a member's
 * own idea, this is live immediately (approved), not sent through the pending-moderation queue.
 */
public record AdminCreateIdeaRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String problem,
        @NotBlank String solution,
        @Size(max = 300) String targetCustomer,
        @NotNull String stage,
        @Size(max = 100) String category,
        Set<String> tags,
        Set<String> helpNeeded,
        @Size(max = 255) String creatorEmail,
        String publisherIdentity
) {
}
