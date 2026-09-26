package com.nukkad.idea.entity;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.common.publishing.PublisherIdentity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ideas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Idea {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String problem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String solution;

    @Column(name = "target_customer", length = 300)
    private String targetCustomer;

    @Convert(converter = IdeaStageConverter.class)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IdeaStage stage = IdeaStage.CONCEPT;

    @Column(length = 100)
    private String category;

    @Column(name = "creator_id", nullable = false, columnDefinition = "CHAR(36)")
    private String creatorId;

    /** True only for an idea an admin published from the admin panel without attributing it to a
     *  member — {@code creatorId} is then the admin's own account, but the public-facing creator
     *  shown for it is {@code publisherIdentity} instead (same idea as Post — see PostCard.tsx /
     *  IdeaDetailPage). */
    @Column(name = "posted_as_platform", nullable = false)
    @Builder.Default
    private boolean postedAsPlatform = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "publisher_identity", nullable = false, length = 30)
    @Builder.Default
    private PublisherIdentity publisherIdentity = PublisherIdentity.BUILDADDA;

    @Column(name = "chapter_id", columnDefinition = "CHAR(36)")
    private String chapterId;

    @Column(name = "startup_id", columnDefinition = "CHAR(36)")
    private String startupId;

    @Column(name = "removed_by_admin", nullable = false)
    @Builder.Default
    private boolean removedByAdmin = false;

    @Column(name = "removal_reason", length = 500)
    private String removalReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    @Builder.Default
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "moderation_reviewed_by", columnDefinition = "CHAR(36)")
    private String moderationReviewedBy;

    @Column(name = "moderation_reviewed_at")
    private Instant moderationReviewedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "idea_tags", joinColumns = @JoinColumn(name = "idea_id"))
    @Column(name = "tag", nullable = false)
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "idea_help_needed", joinColumns = @JoinColumn(name = "idea_id"))
    @Convert(converter = ContributionAreaConverter.class)
    @Column(name = "contribution_area", nullable = false)
    @Builder.Default
    private Set<ContributionArea> helpNeeded = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "idea_team_members", joinColumns = @JoinColumn(name = "idea_id"))
    @Column(name = "user_id", nullable = false)
    @Builder.Default
    private Set<String> teamMemberIds = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
