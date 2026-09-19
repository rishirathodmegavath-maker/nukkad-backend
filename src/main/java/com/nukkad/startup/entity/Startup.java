package com.nukkad.startup.entity;

import com.nukkad.common.moderation.ModerationStatus;
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
@Table(name = "startups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Startup {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(length = 200)
    private String location;

    @Column(length = 500)
    private String website;

    @Column(length = 300)
    private String tagline;

    @Column(length = 100)
    private String sector;

    @Column(columnDefinition = "TEXT")
    private String problem;

    @Column(columnDefinition = "TEXT")
    private String solution;

    @Column(name = "target_customer", columnDefinition = "TEXT")
    private String targetCustomer;

    @Column(name = "business_model", columnDefinition = "TEXT")
    private String businessModel;

    @Column(name = "what_building", columnDefinition = "TEXT")
    private String whatBuilding;

    @Convert(converter = StartupStageConverter.class)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StartupStage stage = StartupStage.IDEA;

    /** Legacy free-text traction blob — kept exactly as-is for backward compatibility. Its value
     *  was copied into {@code otherTraction} by V54; new writes should go through the structured
     *  fields below instead. */
    @Column(columnDefinition = "TEXT")
    private String traction;

    @Column(length = 200)
    private String revenue;

    @Column(length = 200)
    private String customers;

    @Column(length = 200)
    private String users;

    @Column(length = 200)
    private String growth;

    @Column(name = "other_traction", columnDefinition = "TEXT")
    private String otherTraction;

    /** Normalized, comma-separated free-text keywords the founder supplies for search. */
    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Convert(converter = StartupVisibilityConverter.class)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StartupVisibility visibility = StartupVisibility.PUBLIC;

    @Column(name = "fundraising_visible", nullable = false)
    @Builder.Default
    private boolean fundraisingVisible = true;

    @Column(name = "idea_id", columnDefinition = "CHAR(36)")
    private String ideaId;

    @Column(name = "chapter_id", columnDefinition = "CHAR(36)")
    private String chapterId;

    @Column(name = "is_raising", nullable = false)
    @Builder.Default
    private boolean isRaising = false;

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
    @CollectionTable(name = "startup_needs", joinColumns = @JoinColumn(name = "startup_id"))
    @Column(name = "need", nullable = false)
    @Builder.Default
    private Set<String> needs = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
