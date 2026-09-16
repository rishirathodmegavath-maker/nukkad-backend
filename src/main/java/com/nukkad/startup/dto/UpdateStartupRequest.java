package com.nukkad.startup.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateStartupRequest(
        @Size(max = 200) String name,
        @Size(max = 500) String logoUrl,
        @Size(max = 200) String location,
        @Size(max = 500) String website,
        @Size(max = 300) String tagline,
        @Size(max = 100) String sector,
        String problem,
        String solution,
        String targetCustomer,
        String businessModel,
        String whatBuilding,
        String stage,
        String traction,
        @Size(max = 200) String revenue,
        @Size(max = 200) String customers,
        @Size(max = 200) String users,
        @Size(max = 200) String growth,
        String otherTraction,
        String keywords,
        String visibility,
        Boolean fundraisingVisible,
        Boolean isRaising,
        Set<String> needs
) {
}
