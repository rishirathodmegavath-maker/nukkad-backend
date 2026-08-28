package com.nukkad.user.dto;

public record UpdateAppearanceSettingsRequest(
        String themeMode,
        String themePreset,
        String customPrimaryColor,
        String sidebarColor,
        String pageBgColor,
        String cardBgColor,
        String headerBgColor,
        String borderColor,
        String secondarySurfaceColor,
        Boolean resetToDefault,
        Boolean clearAdvancedOverrides
) {
}
