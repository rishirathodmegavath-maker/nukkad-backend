package com.nukkad.investor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * A founder's introduction request to a catalog {@link Investor} that has no linked live account
 * ({@link Investor#getLinkedInvestorProfileId()} is null) — there's no BuildAdda user to notify or open a
 * conversation with, so this is simply recorded for an admin to see and follow up on outside the app. When a
 * catalog investor IS linked to a live account, a request instead goes through the existing
 * {@code com.nukkad.investor.entity.IntroRequest} pipeline and never creates one of these. See
 * {@code InvestorCatalogService#requestIntroduction} for the branch, and the V88 migration for the rationale.
 */
@Entity
@Table(name = "investor_intro_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestorIntroRequest {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "investor_id", nullable = false, columnDefinition = "CHAR(36)")
    private String investorId;

    @Column(name = "requester_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String requesterUserId;

    @Column(name = "startup_id", nullable = false, columnDefinition = "CHAR(36)")
    private String startupId;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private InvestorIntroRequestStatus status = InvestorIntroRequestStatus.PENDING;

    @Column(name = "closed_by_admin_id", columnDefinition = "CHAR(36)")
    private String closedByAdminId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
