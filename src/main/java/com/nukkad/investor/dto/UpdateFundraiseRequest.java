package com.nukkad.investor.dto;

public record UpdateFundraiseRequest(
        Long targetAmount,
        Long amountRaised,
        String fundingStage,
        String useOfFunds,
        Long minimumTicket
) {
}
