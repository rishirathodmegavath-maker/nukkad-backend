package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

/**
 * An admin creating a chapter from the admin panel. Unlike every other Admin*CreateRequest in this
 * package, there is no "unattributed platform" fallback and no publisherIdentity — a chapter's
 * president is a real governance role (approves members, manages chapter events), not a byline, so
 * {@code presidentEmail} is required and must resolve to an existing, active member. The admin
 * account itself is never installed as president.
 */
public record AdminCreateChapterRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String city,
        @Size(max = 100) String country,
        @NotBlank String description,
        @Size(max = 500) String coverImageUrl,
        @Size(max = 500) String logoUrl,
        LocalDate foundedAt,
        @Size(max = 150) String institution,
        @Size(max = 50) String type,
        Set<@Size(max = 50) String> focusAreas,
        @NotBlank @Size(max = 255) String presidentEmail
) {
}
