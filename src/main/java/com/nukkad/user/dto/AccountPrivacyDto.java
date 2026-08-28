package com.nukkad.user.dto;

public record AccountPrivacyDto(
        String profileVisibility,
        String messagePermission,
        String connectPermission
) {
}
