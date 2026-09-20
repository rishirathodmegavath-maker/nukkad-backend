package com.nukkad.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** "Forgot PIN": proven with the account password instead of the old PIN. */
public record ResetWalletPinRequest(
        @NotBlank @Size(max = 128) String password,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be exactly 6 digits") String newPin
) {
}
