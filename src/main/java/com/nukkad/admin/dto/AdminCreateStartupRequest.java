package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * An admin adding a startup from the admin panel. Every field a member can set when registering their own startup
 * (see CreateStartupRequest) is available here too — the logo is the one exception, since it goes through its own
 * upload call once the startup exists (POST /api/admin/startups/{id}/logo), exactly like the member flow.
 * {@code founderEmail} is optional: with it, that member becomes the founder and can manage the startup; without it,
 * the admin's own account owns it.
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
        @Size(max = 200) String location,
        @Size(max = 500) String website,
        @Size(max = 5000) String targetCustomer,
        @Size(max = 5000) String businessModel,
        @Size(max = 5000) String whatBuilding,
        @Size(max = 200) String revenue,
        @Size(max = 200) String customers,
        @Size(max = 200) String users,
        @Size(max = 200) String growth,
        @Size(max = 5000) String otherTraction,
        String visibility,
        Boolean fundraisingVisible,
        @Size(max = 255) String founderEmail
) {
}
