package com.nukkad.investor.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Every field is optional: what is left out stays as it was. */
public record UpdateFundraiseRequest(
        @Positive Long targetAmount,
        @PositiveOrZero Long amountRaised,
        String fundingStage,
        @Size(max = 5000) String useOfFunds,
        @PositiveOrZero Long minimumTicket
) {
}
