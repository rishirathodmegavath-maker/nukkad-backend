package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * An admin adding a startup from the admin panel. The startup fields are the same as a member registering their own
 * (see CreateStartupRequest). {@code founderEmail} is optional: with it, that member becomes the founder and can manage
 * the startup; without it, the admin's own account owns it.
 */
public record AdminCreateStartupRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 300) String tagline,
        @Size(max = 100) String sector,
        String problem,
        String solution,
        String stage,
        Set<@Size(max = 100, message = "each item must be 100 characters or fewer") String> needs,
        String chapterId,
        @Size(max = 255) String founderEmail
) {
}
