package com.nukkad.discussion.entity;

import java.io.Serializable;
import java.util.Objects;

public class PostFollowId implements Serializable {
    private String userId;
    private String postId;

    public PostFollowId() {}

    public PostFollowId(String userId, String postId) {
        this.userId = userId;
        this.postId = postId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostFollowId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(postId, that.postId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, postId);
    }
}
