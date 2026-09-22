package com.nukkad.investor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestInvestorIntroductionRequest(
        @NotBlank String startupId,
        @NotBlank @Size(max = 1000) String message
) {
}
