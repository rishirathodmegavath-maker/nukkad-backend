package com.nukkad.admin.dto;

import java.time.Instant;
import java.util.Set;

public record AdminInvestorDto(
        String id,
        /** The source dataset's own id (CSV "id" column) — null for a hand-created row. */
        String externalSourceId,
        String name,
        String investorType,
        String description,
        String location,
        String country,
        String website,
        String domain,
        String logoUrl,
        Set<String> sectors,
        Set<String> stages,
        Set<String> programs,
        Integer investmentCount,
        Integer exitCount,
        Set<String> keyPeople,
        String facebookUrl,
        String instagramUrl,
        String linkedinUrl,
        String twitterUrl,
        Long chequeMin,
        Long chequeMax,
        boolean active,
        boolean visible,
        // ---- Admin-only contact details — never sent to founders (see InvestorDto) ----
        String contactEmail,
        Boolean contactEmailVerified,
        String secondaryEmail,
        String phoneNumber,
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
