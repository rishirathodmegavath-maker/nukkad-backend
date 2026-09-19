package com.nukkad.investor.entity;

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

/** The admin-reviewed gate {@link com.nukkad.investor.service.InvestorProfileService#create}
 *  previously bypassed entirely — see the V77 migration comment. Field shape mirrors
 *  {@link InvestorProfile} exactly because approval turns one of these into exactly that. */
@Entity
@Table(name = "investor_activation_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestorActivationRequest {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "requester_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String requesterUserId;

    @Convert(converter = InvestorTypeConverter.class)
    @Column(name = "investor_type", nullable = false, length = 20)
    private InvestorType investorType;

    @Column(name = "firm_name", length = 200)
    private String firmName;

    @Column(columnDefinition = "TEXT")
    private String thesis;

    @Column(name = "ticket_min")
    private Long ticketMin;

    @Column(name = "ticket_max")
    private Long ticketMax;

    @Column(name = "portfolio_count", nullable = false)
    @Builder.Default
    private int portfolioCount = 0;

    @Column(length = 300)
    private String website;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "investor_activation_request_sectors", joinColumns = @JoinColumn(name = "investor_activation_request_id"))
    @Column(name = "sector", nullable = false)
    @Builder.Default
    private Set<String> sectors = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "investor_activation_request_stages", joinColumns = @JoinColumn(name = "investor_activation_request_id"))
    @Column(name = "stage", nullable = false)
    @Builder.Default
    private Set<String> stages = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "investor_activation_request_geographies", joinColumns = @JoinColumn(name = "investor_activation_request_id"))
    @Column(name = "geography", nullable = false)
    @Builder.Default
    private Set<String> geographies = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvestorActivationStatus status = InvestorActivationStatus.PENDING;

    @Column(name = "resulting_profile_id", columnDefinition = "CHAR(36)")
    private String resultingProfileId;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "reviewed_by", columnDefinition = "CHAR(36)")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
