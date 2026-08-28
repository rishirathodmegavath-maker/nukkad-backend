package com.nukkad.user.repository;

import com.nukkad.user.entity.UserAppearanceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAppearanceSettingsRepository extends JpaRepository<UserAppearanceSettings, String> {
}
