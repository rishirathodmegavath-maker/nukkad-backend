package com.nukkad.user.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.user.entity.ThemeMode;
import com.nukkad.user.entity.ThemePreset;
import com.nukkad.user.entity.UserAppearanceSettings;
import com.nukkad.user.repository.UserAppearanceSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class UserAppearanceSettingsService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final UserAppearanceSettingsRepository repository;

    public UserAppearanceSettingsService(UserAppearanceSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UserAppearanceSettings getSettings(String userId) {
        return repository.findById(userId)
                .orElseGet(() -> UserAppearanceSettings.builder().userId(userId).build());
    }

    @Transactional
    public UserAppearanceSettings updateSettings(String userId, ThemeMode themeMode, ThemePreset themePreset,
                                                  String customPrimaryColor, String sidebarColor, String pageBgColor,
                                                  String cardBgColor, String headerBgColor, String borderColor,
                                                  String secondarySurfaceColor) {
        requireValidHexOrNull(customPrimaryColor, "customPrimaryColor");
        requireValidHexOrNull(sidebarColor, "sidebarColor");
        requireValidHexOrNull(pageBgColor, "pageBgColor");
        requireValidHexOrNull(cardBgColor, "cardBgColor");
        requireValidHexOrNull(headerBgColor, "headerBgColor");
        requireValidHexOrNull(borderColor, "borderColor");
        requireValidHexOrNull(secondarySurfaceColor, "secondarySurfaceColor");

        UserAppearanceSettings settings = repository.findById(userId)
                .orElseGet(() -> UserAppearanceSettings.builder().userId(userId).build());
        if (themeMode != null) settings.setThemeMode(themeMode);
        if (themePreset != null) settings.setThemePreset(themePreset);
        if (customPrimaryColor != null) settings.setCustomPrimaryColor(customPrimaryColor);
        if (sidebarColor != null) settings.setSidebarColor(sidebarColor);
        if (pageBgColor != null) settings.setPageBgColor(pageBgColor);
        if (cardBgColor != null) settings.setCardBgColor(cardBgColor);
        if (headerBgColor != null) settings.setHeaderBgColor(headerBgColor);
        if (borderColor != null) settings.setBorderColor(borderColor);
        if (secondarySurfaceColor != null) settings.setSecondarySurfaceColor(secondarySurfaceColor);
        return repository.save(settings);
    }

    /** Clears every advanced-override and custom-color field, resetting to the given preset in the given mode. */
    @Transactional
    public UserAppearanceSettings resetToDefault(String userId, ThemeMode themeMode, ThemePreset themePreset) {
        UserAppearanceSettings settings = UserAppearanceSettings.builder()
                .userId(userId)
                .themeMode(themeMode != null ? themeMode : ThemeMode.SYSTEM)
                .themePreset(themePreset != null ? themePreset : ThemePreset.NUKKAD_INDIGO)
                .build();
        return repository.save(settings);
    }

    /** Clears only the 6 advanced-override colours, leaving mode/preset/custom colour untouched. */
    @Transactional
    public UserAppearanceSettings clearAdvancedOverrides(String userId) {
        UserAppearanceSettings settings = repository.findById(userId)
                .orElseGet(() -> UserAppearanceSettings.builder().userId(userId).build());
        settings.setSidebarColor(null);
        settings.setPageBgColor(null);
        settings.setCardBgColor(null);
        settings.setHeaderBgColor(null);
        settings.setBorderColor(null);
        settings.setSecondarySurfaceColor(null);
        return repository.save(settings);
    }

    private void requireValidHexOrNull(String value, String fieldName) {
        if (value != null && !value.isBlank() && !HEX_COLOR.matcher(value).matches()) {
            throw new BadRequestException("Invalid hex colour for " + fieldName + ": " + value);
        }
    }
}
