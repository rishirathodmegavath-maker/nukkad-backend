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

/** "Following" a discussion — same composite-key toggle shape as {@code StartupFollow}. */
@Entity
@Table(name = "post_follows")
@IdClass(PostFollowId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostFollow {

    @jakarta.persistence.Id
    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userId;

    @jakarta.persistence.Id
    @Column(name = "post_id", nullable = false, columnDefinition = "CHAR(36)")
    private String postId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
