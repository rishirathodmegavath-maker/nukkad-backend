package com.nukkad.investor.dto;

import java.time.Instant;

/** A "recorded" introduction request — see {@code InvestorIntroRequest} for when this path (rather than the live
 *  {@code IntroRequest} pipeline) is used. */
public record InvestorIntroRequestDto(
        String id,
        String investorId,
        String investorName,
        String requesterUserId,
        String requesterName,
        String startupId,
        String startupName,
        String message,
        String status,
        Instant createdAt,
        Instant closedAt
) {
}
