package com.nukkad.investor.dto;

import java.time.Instant;
import java.util.Set;

/** Founder-facing: what Investor Discovery shows. Never carries {@code linkedInvestorProfileId} — that's an
 *  internal routing detail (see {@code Investor} entity), only {@code AdminInvestorDto} exposes it. */
public record InvestorDto(
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
        Instant createdAt
) {
}
