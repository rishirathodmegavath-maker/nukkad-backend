package com.nukkad.feed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * How much a user's behavior has favored one topic — a hashtag they've engaged with, or a
 * {@link Post.Type}, the only two categorization signals that exist on a Post today. {@code score}
 * accumulates on every relevant signal (see {@code UserTopicAffinityService}) and is decayed at
 * READ time rather than by a scheduled job, so a stale row is never wrong, just older.
 */
@Entity
@Table(name = "user_topic_affinity",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_topic_affinity", columnNames = {"user_id", "topic_kind", "topic_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTopicAffinity {

    public enum TopicKind { HASHTAG, POST_TYPE }

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "topic_kind", nullable = false, length = 20)
    private TopicKind topicKind;

    @Column(name = "topic_key", nullable = false, length = 50)
    private String topicKey;

    @Column(nullable = false)
    @Builder.Default
    private double score = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
