package com.nukkad.auth.dto;

public record RefreshTokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
