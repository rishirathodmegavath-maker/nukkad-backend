package com.nukkad.startup.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateStartupRequest(
        // Optional (null leaves the name alone), but a name that is sent can't be empty or only spaces.
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 200) String name,
        @Size(max = 500) String logoUrl,
        @Size(max = 200) String location,
        @Size(max = 500) String website,
        @Size(max = 300) String tagline,
        @Size(max = 100) String sector,
        @Size(max = 5000) String problem,
        @Size(max = 5000) String solution,
        @Size(max = 5000) String targetCustomer,
        @Size(max = 5000) String businessModel,
        @Size(max = 5000) String whatBuilding,
        String stage,
        @Size(max = 5000) String traction,
        @Size(max = 200) String revenue,
        @Size(max = 200) String customers,
        @Size(max = 200) String users,
        @Size(max = 200) String growth,
        @Size(max = 5000) String otherTraction,
        @Size(max = 2000) String keywords,
        String visibility,
        Boolean fundraisingVisible,
        Boolean isRaising,
        Set<@Size(max = 100, message = "each item must be 100 characters or fewer") String> needs
) {
}
