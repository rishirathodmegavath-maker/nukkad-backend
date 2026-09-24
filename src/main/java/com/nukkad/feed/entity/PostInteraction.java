package com.nukkad.feed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * One real behavioral-signal row per action, piggybacked on the endpoints that already exist
 * (like/save/comment/hide/open) — never a per-feed-response IMPRESSION row, which would be the
 * highest-volume, lowest-signal row this design could have (see the personalized feed plan).
 */
@Entity
@Table(name = "post_interactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostInteraction {

    public enum Type { OPEN, LIKE, UNLIKE, SAVE, UNSAVE, SHARE, COMMENT, HIDE, UNHIDE }

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userId;

    @Column(name = "post_id", nullable = false, columnDefinition = "CHAR(36)")
    private String postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, length = 20)
    private Type interactionType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
