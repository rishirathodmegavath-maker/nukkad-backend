package com.nukkad.auth.dto;

import com.nukkad.user.dto.UserDto;

public record AuthResponse(UserDto user, String accessToken, String refreshToken, long expiresIn, boolean isNewUser) {
}
