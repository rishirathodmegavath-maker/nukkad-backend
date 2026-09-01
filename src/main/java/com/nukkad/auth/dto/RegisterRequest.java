package com.nukkad.auth.dto;

import com.nukkad.auth.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Size(max = 100) @StrongPassword String password
) {
}
