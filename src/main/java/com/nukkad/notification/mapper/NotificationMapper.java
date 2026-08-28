package com.nukkad.notification.mapper;

import com.nukkad.notification.dto.NotificationDto;
import com.nukkad.notification.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationDto toDto(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getUserId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getRelatedId(),
                notification.getActorUserId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
