package com.nukkad.admin.dto;

import java.time.Instant;
import java.util.Set;

public record AdminInvestorDto(
        String id,
        String name,
        String investorType,
        String description,
        String location,
        String website,
        String logoUrl,
        Set<String> sectors,
        Set<String> stages,
        Long chequeMin,
        Long chequeMax,
        boolean active,
        boolean visible,
        /** Set when this catalog row is tied to a real, activated investor account — see the {@code Investor}
         *  entity's class comment. Requests to a linked row go through the live introduction pipeline instead
         *  of being merely recorded. */
        String linkedInvestorProfileId,
        String linkedInvestorProfileName,
        String createdByAdminId,
        Instant createdAt,
        Instant updatedAt
) {
}
