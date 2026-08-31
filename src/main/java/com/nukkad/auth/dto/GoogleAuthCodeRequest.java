package com.nukkad.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthCodeRequest(@NotBlank String code, @NotBlank String redirectUri) {
}
