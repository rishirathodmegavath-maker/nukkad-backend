package com.nukkad.user.repository;

import com.nukkad.user.entity.UserPrivacySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPrivacySettingsRepository extends JpaRepository<UserPrivacySettings, String> {
}
