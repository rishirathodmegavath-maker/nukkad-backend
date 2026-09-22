package com.nukkad.investor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateFundraiseRequest(
        @NotBlank String startupId,
        @Positive @NotNull Long targetAmount,
        @NotBlank String fundingStage,
        @Size(max = 5000) String useOfFunds,
        @PositiveOrZero Long minimumTicket
) {
}
