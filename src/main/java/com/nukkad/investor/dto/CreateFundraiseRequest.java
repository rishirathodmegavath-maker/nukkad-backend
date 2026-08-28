package com.nukkad.investor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateFundraiseRequest(
        @NotBlank String startupId,
        @Positive @NotNull Long targetAmount,
        @NotBlank String fundingStage,
        String useOfFunds,
        Long minimumTicket
) {
}
