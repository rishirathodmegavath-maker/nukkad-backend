package com.nukkad.messaging.entity;

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

/** Per-viewer message hiding: a row here means `userId` deleted `messageId` from their own view — the shared Message row is untouched. */
@Entity
@Table(name = "message_deletions", uniqueConstraints = @UniqueConstraint(name = "uq_message_deletion_user", columnNames = {"message_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDeletion {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "message_id", nullable = false, columnDefinition = "CHAR(36)")
    private String messageId;

    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userId;

    @CreationTimestamp
    @Column(name = "deleted_at", nullable = false, updatable = false)
    private Instant deletedAt;
}
