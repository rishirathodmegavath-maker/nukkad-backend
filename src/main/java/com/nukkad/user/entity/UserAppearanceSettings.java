package com.nukkad.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_appearance_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAppearanceSettings {

    @Id
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 10)
    @Builder.Default
    private ThemeMode themeMode = ThemeMode.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_preset", nullable = false, length = 30)
    @Builder.Default
    private ThemePreset themePreset = ThemePreset.NUKKAD_INDIGO;

    @Column(name = "custom_primary_color", length = 7)
    private String customPrimaryColor;

    @Column(name = "sidebar_color", length = 7)
    private String sidebarColor;

    @Column(name = "page_bg_color", length = 7)
    private String pageBgColor;

    @Column(name = "card_bg_color", length = 7)
    private String cardBgColor;

    @Column(name = "header_bg_color", length = 7)
    private String headerBgColor;

    @Column(name = "border_color", length = 7)
    private String borderColor;

    @Column(name = "secondary_surface_color", length = 7)
    private String secondarySurfaceColor;
}
