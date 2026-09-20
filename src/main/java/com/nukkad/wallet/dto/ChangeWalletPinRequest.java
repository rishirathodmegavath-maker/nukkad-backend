package com.nukkad.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeWalletPinRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be exactly 6 digits") String currentPin,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be exactly 6 digits") String newPin
) {
}
