package com.nukkad.notification.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserNotificationMuteId implements Serializable {
    private String userId;
    private NotificationType type;

    public UserNotificationMuteId() {}

    public UserNotificationMuteId(String userId, NotificationType type) {
        this.userId = userId;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserNotificationMuteId that)) return false;
        return Objects.equals(userId, that.userId) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, type);
    }
}
