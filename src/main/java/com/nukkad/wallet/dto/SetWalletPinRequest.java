package com.nukkad.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** First-time PIN creation. The account password is required so a session someone merely walked up
 *  to cannot create (and thereby take over) the wallet PIN. */
public record SetWalletPinRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be exactly 6 digits") String pin,
        @NotBlank @Size(max = 128) String password
) {
}
