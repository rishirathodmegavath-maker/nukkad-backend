package com.nukkad.notification.repository;

import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.entity.UserNotificationMute;
import com.nukkad.notification.entity.UserNotificationMuteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserNotificationMuteRepository extends JpaRepository<UserNotificationMute, UserNotificationMuteId> {
    List<UserNotificationMute> findByUserId(String userId);
    boolean existsByUserIdAndType(String userId, NotificationType type);
    void deleteByUserIdAndType(String userId, NotificationType type);
}
