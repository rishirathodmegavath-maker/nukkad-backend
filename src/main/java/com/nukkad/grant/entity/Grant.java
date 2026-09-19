package com.nukkad.grant.entity;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupStageConverter;
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
@Table(name = "grants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Grant {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 200)
    private String provider;

    @Convert(converter = GrantProviderTypeConverter.class)
    @Column(name = "provider_type", nullable = false, length = 20)
    @Builder.Default
    private GrantProviderType providerType = GrantProviderType.OTHER;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Free text — grants rarely have a single numeric figure (e.g. "Up to ₹50L, equity-free"). */
    @Column(name = "funding_amount", length = 200)
    private String fundingAmount;

    @Column(name = "eligibility_criteria", columnDefinition = "TEXT")
    private String eligibilityCriteria;

    /** Empty means open to every sector. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_eligible_sectors", joinColumns = @JoinColumn(name = "grant_id"))
    @Column(name = "sector", nullable = false)
    @Builder.Default
    private Set<String> eligibleSectors = new HashSet<>();

    /** Empty means open to every stage. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_eligible_stages", joinColumns = @JoinColumn(name = "grant_id"))
    @Convert(converter = StartupStageConverter.class)
    @Column(name = "stage", nullable = false)
    @Builder.Default
    private Set<StartupStage> eligibleStages = new HashSet<>();

    /** Null means rolling / no fixed deadline. */
    private Instant deadline;

    /** Always an external link — Nukkad never hosts the application itself. */
    @Column(name = "application_url", nullable = false, length = 500)
    private String applicationUrl;

    @Column(name = "created_by_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String createdByUserId;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
