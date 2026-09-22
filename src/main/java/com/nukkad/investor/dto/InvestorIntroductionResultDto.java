package com.nukkad.investor.dto;

/**
 * What happened when a founder requested an introduction to a catalog investor — exactly one of the two is set.
 * {@code LIVE}: the catalog row is linked to a real investor account, so this went through the existing
 * {@code IntroRequest} pipeline (notification, and a conversation once accepted) unchanged.
 * {@code RECORDED}: there's no live account behind this catalog row, so the request was simply logged for an
 * admin to follow up on outside the app — see {@code InvestorIntroRequest}.
 */
public record InvestorIntroductionResultDto(
        String kind,
        IntroRequestDto liveRequest,
        InvestorIntroRequestDto recordedRequest
) {
}
