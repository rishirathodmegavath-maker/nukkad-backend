package com.nukkad.startup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * A new startup. Only {@code name} is required. Everything after {@code chapterId} is optional and can also be set
 * later from the startup's profile; the create-startup flow sends it all at once so the startup is created complete
 * (or not at all) rather than half-filled if one field is rejected. Omitted visibility means Public and omitted
 * fundraising visibility means visible, exactly as for a startup created without them.
 */
public record CreateStartupRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String logoUrl,
        @Size(max = 300) String tagline,
        @Size(max = 100) String sector,
        @Size(max = 5000) String problem,
        @Size(max = 5000) String solution,
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
        Boolean fundraisingVisible
) {

    /** The original nine fields, for callers that only ever set those (the admin "Add startup" form, older clients). */
    public CreateStartupRequest(String name, String logoUrl, String tagline, String sector, String problem, String solution,
                                String stage, Set<String> needs, String chapterId) {
        this(name, logoUrl, tagline, sector, problem, solution, stage, needs, chapterId,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
