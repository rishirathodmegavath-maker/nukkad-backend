package com.nukkad.startup.entity;

import java.io.Serializable;
import java.util.Objects;

public class StartupFollowId implements Serializable {
    private String userId;
    private String startupId;

    public StartupFollowId() {}

    public StartupFollowId(String userId, String startupId) {
        this.userId = userId;
        this.startupId = startupId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StartupFollowId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(startupId, that.startupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, startupId);
    }
}
