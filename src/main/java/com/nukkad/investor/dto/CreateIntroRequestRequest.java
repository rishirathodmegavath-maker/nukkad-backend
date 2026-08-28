package com.nukkad.investor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIntroRequestRequest(
        @NotBlank String recipientId,
        @NotBlank String direction,
        String startupId,
        String ideaId,
        @NotBlank @Size(max = 1000) String message
) {
}
