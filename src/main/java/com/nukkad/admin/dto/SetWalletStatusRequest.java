package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mirrors {@code AdminUserService.UpdateUserStatusCommand}'s shape for the same reason: a
 *  status change is self-documenting only if the reason travels with it into the audit log. */
public record SetWalletStatusRequest(
        @NotBlank String status,
        @Size(max = 500) String reason
) {
}
