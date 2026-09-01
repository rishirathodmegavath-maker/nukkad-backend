package com.nukkad.auth.dto;

import com.nukkad.auth.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmDto(@NotBlank String token, @NotBlank @Size(max = 100) @StrongPassword String newPassword) {
}
