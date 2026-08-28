package com.nukkad.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Interaction table (raw ID columns, not {@code @ManyToOne}) — mirrors the feed module's PostLike shape, not the {@code @ManyToOne}-based profile-content sub-entities. */
@Entity
@Table(name = "user_endorsements", uniqueConstraints = @UniqueConstraint(
        name = "uq_user_endorsements_triple", columnNames = {"endorsed_user_id", "endorser_user_id", "skill"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEndorsement {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "endorsed_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String endorsedUserId;

    @Column(name = "endorser_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String endorserUserId;

    @Column(nullable = false, length = 100)
    private String skill;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
