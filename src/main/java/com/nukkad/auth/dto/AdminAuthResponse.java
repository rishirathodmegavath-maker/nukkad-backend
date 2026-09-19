package com.nukkad.auth.dto;

public record AdminAuthResponse(AdminIdentity admin, String accessToken, String refreshToken, long expiresIn) {
}
