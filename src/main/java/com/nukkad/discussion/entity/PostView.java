package com.nukkad.discussion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One page view of a discussion. Mirrors {@code StartupProfileView} exactly: not deduplicated (every
 * read inserts a new row, so a refresh does inflate the count — the same tradeoff already accepted
 * for startup profile views), {@code viewerId} nullable for an anonymous viewer.
 */
@Entity
@Table(name = "post_views")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostView {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "post_id", nullable = false, columnDefinition = "CHAR(36)")
    private String postId;

    @Column(name = "viewer_id", columnDefinition = "CHAR(36)")
    private String viewerId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
