package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code role} must be "ADMIN" — the only role an Admin may assign through this endpoint. Every
 *  other {@link com.nukkad.user.entity.SecurityRole} is earned through its own domain action
 *  (activating an investor profile, founding a chapter) and is not administratively assignable. */
public record UpdateUserRoleRequest(@NotBlank String role, @NotNull Boolean grant) {
}
