package com.nukkad.user.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserFollowId implements Serializable {
    private String followerId;
    private String followeeId;

    public UserFollowId() {}

    public UserFollowId(String followerId, String followeeId) {
        this.followerId = followerId;
        this.followeeId = followeeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserFollowId that)) return false;
        return Objects.equals(followerId, that.followerId) && Objects.equals(followeeId, that.followeeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(followerId, followeeId);
    }
}
