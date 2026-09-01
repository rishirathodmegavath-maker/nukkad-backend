package com.nukkad.auth.dto;

import com.nukkad.auth.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(max = 100) @StrongPassword String newPassword
) {
}
