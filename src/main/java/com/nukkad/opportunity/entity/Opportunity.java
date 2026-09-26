package com.nukkad.opportunity.entity;

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
import jakarta.persistence.OrderColumn;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "opportunities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Opportunity {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String title;

    @Convert(converter = OpportunityTypeConverter.class)
    @Column(nullable = false, length = 20)
    private OpportunityType type;

    @Column(nullable = false)
    @Builder.Default
    private boolean closed = false;

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

    @Column(name = "startup_id", columnDefinition = "CHAR(36)")
    private String startupId;

    @Column(name = "organization_name", nullable = false, length = 200)
    private String organizationName;

    @Column(length = 200)
    private String location;

    @Convert(converter = WorkModeConverter.class)
    @Column(name = "work_mode", nullable = false, length = 20)
    @Builder.Default
    private WorkMode workMode = WorkMode.IN_PERSON;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String responsibilities;

    @Column(length = 200)
    private String compensation;

    @Column(length = 100)
    private String equity;

    @Column(name = "experience_level", length = 100)
    private String experienceLevel;

    @Column(name = "application_deadline")
    private Instant applicationDeadline;

    @Column(name = "posted_by_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String postedByUserId;

    /** True only for an opportunity an admin published from the admin panel without attributing it
     *  to a member — {@code postedByUserId} is then the admin's own account, but the public-facing
     *  poster shown for it is {@code publisherIdentity} instead (same idea as Post — see PostCard.tsx). */
    @Column(name = "posted_as_platform", nullable = false)
    @Builder.Default
    private boolean postedAsPlatform = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "publisher_identity", nullable = false, length = 30)
    @Builder.Default
    private PublisherIdentity publisherIdentity = PublisherIdentity.BUILDADDA;

    @Column(name = "chapter_id", columnDefinition = "CHAR(36)")
    private String chapterId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "opportunity_requirements", joinColumns = @JoinColumn(name = "opportunity_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "requirement", nullable = false)
    @Builder.Default
    private List<String> requirements = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "opportunity_required_skills", joinColumns = @JoinColumn(name = "opportunity_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "skill", nullable = false)
    @Builder.Default
    private List<String> requiredSkills = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
