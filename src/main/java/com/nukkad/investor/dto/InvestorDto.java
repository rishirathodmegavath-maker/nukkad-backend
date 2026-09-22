package com.nukkad.investor.dto;

import java.time.Instant;
import java.util.Set;

/** Founder-facing: what Investor Discovery shows. Never carries {@code linkedInvestorProfileId} (internal
 *  routing, see the Investor entity) or any private contact detail (contactEmail, contactEmailVerified,
 *  secondaryEmail, phoneNumber, externalSourceId) — only {@code AdminInvestorDto} exposes those. */
public record InvestorDto(
        String id,
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
        Instant createdAt
) {
}
