package com.nukkad.user.service;

import com.nukkad.user.entity.ProfileSection;
import com.nukkad.user.entity.SectionVisibility;
import com.nukkad.user.entity.UserSectionPrivacy;
import com.nukkad.user.repository.UserSectionPrivacyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-section profile visibility. The table is sparse — the absence of a row means PUBLIC, so
 * shipping this doesn't silently hide any existing user's already-visible content.
 */
@Service
public class ProfilePrivacyService {

    private final UserSectionPrivacyRepository repository;

    public ProfilePrivacyService(UserSectionPrivacyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<ProfileSection, SectionVisibility> getSettings(String userId) {
        Map<ProfileSection, SectionVisibility> settings = new EnumMap<>(ProfileSection.class);
        for (ProfileSection section : ProfileSection.values()) settings.put(section, SectionVisibility.PUBLIC);
        for (UserSectionPrivacy row : repository.findByUserId(userId)) settings.put(row.getSection(), row.getVisibility());
        return settings;
    }

    @Transactional
    public Map<ProfileSection, SectionVisibility> updateSettings(String userId, Map<ProfileSection, SectionVisibility> updates) {
        for (Map.Entry<ProfileSection, SectionVisibility> entry : updates.entrySet()) {
            if (entry.getValue() == SectionVisibility.PUBLIC) {
                repository.deleteByUserIdAndSection(userId, entry.getKey());
            } else {
                UserSectionPrivacy row = repository.findByUserIdAndSection(userId, entry.getKey())
                        .orElseGet(() -> UserSectionPrivacy.builder().userId(userId).section(entry.getKey()).build());
                row.setVisibility(entry.getValue());
                repository.save(row);
            }
        }
        return getSettings(userId);
    }

    /** Owner always sees their own sections. {@code connectionStatus} should already be resolved by the caller. */
    @Transactional(readOnly = true)
    public boolean isVisible(String ownerId, String viewerId, ProfileSection section, String connectionStatus) {
        if (viewerId != null && viewerId.equals(ownerId)) return true;
        SectionVisibility visibility = repository.findByUserIdAndSection(ownerId, section)
                .map(UserSectionPrivacy::getVisibility)
                .orElse(SectionVisibility.PUBLIC);
        return switch (visibility) {
            case PUBLIC -> true;
            case CONNECTIONS -> "CONNECTED".equals(connectionStatus);
            case PRIVATE -> false;
        };
    }
}
