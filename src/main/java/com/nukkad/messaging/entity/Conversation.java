package com.nukkad.messaging.entity;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * Canonical: userAId < userBId (string-ordered), enforced by the service layer. userAId/userBId
 * and every per-side column below (mute/nickname/deletedAt) are DIRECT-only — a GROUP row leaves
 * them all null and uses {@link com.nukkad.messaging.entity.ConversationParticipant} instead.
 */
@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(name = "uq_conversation_pair", columnNames = {"user_a_id", "user_b_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    public enum Type { DIRECT, GROUP }

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", nullable = false, length = 20)
    @Builder.Default
    private Type conversationType = Type.DIRECT;

    @Column(name = "group_name", length = 100)
    private String groupName;

    @Column(name = "group_avatar_url", length = 500)
    private String groupAvatarUrl;

    @Column(name = "created_by", columnDefinition = "CHAR(36)")
    private String createdBy;

    @Column(name = "user_a_id", columnDefinition = "CHAR(36)")
    private String userAId;

    @Column(name = "user_b_id", columnDefinition = "CHAR(36)")
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

    /** userAId/userBId are null for a GROUP row — every method below is DIRECT-only and fails loudly
     * rather than NPE'ing if ever misused on a GROUP conversation. */
    private void requireDirect() {
        if (conversationType != Type.DIRECT) {
            throw new IllegalStateException("This method is DIRECT-only; use ConversationParticipant for GROUP conversations");
        }
    }

    public boolean hasParticipant(String userId) {
        requireDirect();
        return userAId.equals(userId) || userBId.equals(userId);
    }

    public String otherParticipant(String userId) {
        requireDirect();
        return userAId.equals(userId) ? userBId : userAId;
    }

    public boolean isMutedFor(String viewerId) {
        requireDirect();
        return userAId.equals(viewerId) ? mutedByUserA : mutedByUserB;
    }

    public void setMutedFor(String viewerId, boolean muted) {
        requireDirect();
        if (userAId.equals(viewerId)) mutedByUserA = muted;
        else mutedByUserB = muted;
    }

    public String nicknameFor(String viewerId) {
        requireDirect();
        return userAId.equals(viewerId) ? nicknameByUserA : nicknameByUserB;
    }

    public void setNicknameFor(String viewerId, String nickname) {
        requireDirect();
        if (userAId.equals(viewerId)) nicknameByUserA = nickname;
        else nicknameByUserB = nickname;
    }

    public void setDeletedAtFor(String viewerId, Instant when) {
        requireDirect();
        if (userAId.equals(viewerId)) deletedAtByUserA = when;
        else deletedAtByUserB = when;
    }
}
