package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code status} is parsed against the server-side {@code AccountStatus} enum — an unrecognized
 *  value is rejected, never coerced or trusted as-is. */
public record UpdateUserStatusRequest(@NotBlank String status, @Size(max = 500) String reason) {
}
