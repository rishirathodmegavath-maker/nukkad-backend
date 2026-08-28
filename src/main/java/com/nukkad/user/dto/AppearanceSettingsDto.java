package com.nukkad.user.dto;

public record AppearanceSettingsDto(
        String themeMode,
        String themePreset,
        String customPrimaryColor,
        String sidebarColor,
        String pageBgColor,
        String cardBgColor,
        String headerBgColor,
        String borderColor,
        String secondarySurfaceColor
) {
}
