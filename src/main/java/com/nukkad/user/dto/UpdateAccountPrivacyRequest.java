package com.nukkad.user.dto;

public record UpdateAccountPrivacyRequest(
        String profileVisibility,
        String messagePermission,
        String connectPermission
) {
}
