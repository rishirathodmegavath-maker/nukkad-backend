package com.nukkad.discussion.entity;

import java.io.Serializable;
import java.util.Objects;

public class PostVoteId implements Serializable {
    private String postId;
    private String userId;

    public PostVoteId() {}

    public PostVoteId(String postId, String userId) {
        this.postId = postId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostVoteId that)) return false;
        return Objects.equals(postId, that.postId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, userId);
    }
}
