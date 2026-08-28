package com.nukkad.user.entity;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/** Interaction table (raw ID columns, not {@code @ManyToOne}) — has two distinct owners (author, subject), unlike the single-owner profile sub-entities. */
@Entity
@Table(name = "user_recommendations", uniqueConstraints = @UniqueConstraint(
        name = "uq_user_recommendations_pair", columnNames = {"subject_user_id", "author_user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRecommendation {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "subject_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String subjectUserId;

    @Column(name = "author_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String authorUserId;

    @Column(length = 100)
    private String relationship;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;
}
