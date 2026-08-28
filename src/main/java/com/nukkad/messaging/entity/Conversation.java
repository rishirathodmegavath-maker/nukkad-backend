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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/** Canonical: userAId < userBId (string-ordered), enforced by the service layer. */
@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(name = "uq_conversation_pair", columnNames = {"user_a_id", "user_b_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "user_a_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userAId;

    @Column(name = "user_b_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userBId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "muted_by_user_a", nullable = false)
    @Builder.Default
    private boolean mutedByUserA = false;

    @Column(name = "muted_by_user_b", nullable = false)
    @Builder.Default
    private boolean mutedByUserB = false;

    @Column(name = "nickname_by_user_a", length = 50)
    private String nicknameByUserA;

    @Column(name = "nickname_by_user_b", length = 50)
    private String nicknameByUserB;

    @Column(name = "deleted_at_by_user_a")
    private Instant deletedAtByUserA;

    @Column(name = "deleted_at_by_user_b")
    private Instant deletedAtByUserB;

    public boolean hasParticipant(String userId) {
        return userAId.equals(userId) || userBId.equals(userId);
    }

    public String otherParticipant(String userId) {
        return userAId.equals(userId) ? userBId : userAId;
    }

    public boolean isMutedFor(String viewerId) {
        return userAId.equals(viewerId) ? mutedByUserA : mutedByUserB;
    }

    public void setMutedFor(String viewerId, boolean muted) {
        if (userAId.equals(viewerId)) mutedByUserA = muted;
        else mutedByUserB = muted;
    }

    public String nicknameFor(String viewerId) {
        return userAId.equals(viewerId) ? nicknameByUserA : nicknameByUserB;
    }

    public void setNicknameFor(String viewerId, String nickname) {
        if (userAId.equals(viewerId)) nicknameByUserA = nickname;
        else nicknameByUserB = nickname;
    }

    public void setDeletedAtFor(String viewerId, Instant when) {
        if (userAId.equals(viewerId)) deletedAtByUserA = when;
        else deletedAtByUserB = when;
    }
}
