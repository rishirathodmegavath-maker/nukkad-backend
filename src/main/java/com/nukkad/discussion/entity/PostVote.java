package com.nukkad.discussion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One member's upvote (+1) or downvote (-1) on a discussion — a signed vote, distinct from the plain
 * heart {@code PostLike}. Composite primary key {@code (postId, userId)}, same shape as
 * {@code StartupFollow}: one vote per member per discussion, toggled/switched rather than accumulated.
 */
@Entity
@Table(name = "post_votes")
@IdClass(PostVoteId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostVote {

    @jakarta.persistence.Id
    @Column(name = "post_id", nullable = false, columnDefinition = "CHAR(36)")
    private String postId;

    @jakarta.persistence.Id
    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userId;

    /** +1 (upvote) or -1 (downvote); never 0 — a removed vote is a deleted row, not a zero value. */
    @Column(nullable = false)
    private int value;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
