package com.nukkad.user.service;

import com.nukkad.user.entity.ConnectPermission;
import com.nukkad.user.entity.MessagePermission;
import com.nukkad.user.entity.ProfileVisibility;
import com.nukkad.user.entity.UserPrivacySettings;
import com.nukkad.user.repository.UserPrivacySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account-level "who can X" settings — distinct from the per-section {@link ProfilePrivacyService}
 * (different table, different concern: this gates whole-profile visibility, messaging, and
 * connect requests, not individual profile sections).
 */
@Service
public class UserPrivacySettingsService {

    private final UserPrivacySettingsRepository repository;

    public UserPrivacySettingsService(UserPrivacySettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UserPrivacySettings getSettings(String userId) {
        return repository.findById(userId)
                .orElseGet(() -> UserPrivacySettings.builder().userId(userId).build());
    }

    @Transactional
    public UserPrivacySettings updateSettings(String userId, ProfileVisibility profileVisibility,
                                               MessagePermission messagePermission, ConnectPermission connectPermission) {
        UserPrivacySettings settings = repository.findById(userId)
                .orElseGet(() -> UserPrivacySettings.builder().userId(userId).build());
        if (profileVisibility != null) settings.setProfileVisibility(profileVisibility);
        if (messagePermission != null) settings.setMessagePermission(messagePermission);
        if (connectPermission != null) settings.setConnectPermission(connectPermission);
        return repository.save(settings);
    }

    /** Owner can always message themselves is not a real case (self-messaging doesn't exist); this is for other viewers. */
    @Transactional(readOnly = true)
    public boolean canMessage(String recipientId, boolean isConnected) {
        MessagePermission permission = getSettings(recipientId).getMessagePermission();
        return switch (permission) {
            case EVERYONE -> true;
            case CONNECTIONS -> isConnected;
        };
    }

    @Transactional(readOnly = true)
    public boolean canConnect(String targetId, boolean hasMutualConnection) {
        ConnectPermission permission = getSettings(targetId).getConnectPermission();
        return switch (permission) {
            case EVERYONE -> true;
            case MUTUAL_CONNECTIONS -> hasMutualConnection;
            case NOBODY -> false;
        };
    }

    @Transactional(readOnly = true)
    public boolean isProfileRestricted(String ownerId, String viewerId, boolean isConnected) {
        if (viewerId != null && viewerId.equals(ownerId)) return false;
        ProfileVisibility visibility = getSettings(ownerId).getProfileVisibility();
        return visibility == ProfileVisibility.CONNECTIONS && !isConnected;
    }
}
