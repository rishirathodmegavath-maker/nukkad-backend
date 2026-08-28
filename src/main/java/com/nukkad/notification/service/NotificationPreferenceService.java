package com.nukkad.notification.service;

import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.entity.UserNotificationMute;
import com.nukkad.notification.repository.UserNotificationMuteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationPreferenceService {

    private final UserNotificationMuteRepository muteRepository;

    public NotificationPreferenceService(UserNotificationMuteRepository muteRepository) {
        this.muteRepository = muteRepository;
    }

    @Transactional(readOnly = true)
    public Map<NotificationType, Boolean> getPreferences(String userId) {
        Set<NotificationType> muted = muteRepository.findByUserId(userId).stream()
                .map(UserNotificationMute::getType)
                .collect(Collectors.toSet());
        Map<NotificationType, Boolean> preferences = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            preferences.put(type, !muted.contains(type));
        }
        return preferences;
    }

    @Transactional
    public void updatePreferences(String userId, Map<NotificationType, Boolean> updates) {
        updates.forEach((type, enabled) -> {
            if (enabled) {
                muteRepository.deleteByUserIdAndType(userId, type);
            } else if (!muteRepository.existsByUserIdAndType(userId, type)) {
                muteRepository.save(UserNotificationMute.builder().userId(userId).type(type).build());
            }
        });
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String userId, NotificationType type) {
        return !muteRepository.existsByUserIdAndType(userId, type);
    }
}
