package com.nukkad.opportunity.entity;

import com.nukkad.user.entity.Availability;
import com.nukkad.user.entity.AvailabilityConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "opportunity_applicants", uniqueConstraints = @UniqueConstraint(name = "uq_oapp", columnNames = {"opportunity_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpportunityApplicant {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "opportunity_id", nullable = false, columnDefinition = "CHAR(36)")
    private String opportunityId;

    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userId;

    @Convert(converter = ApplicationStatusConverter.class)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "why_interested", columnDefinition = "TEXT")
    private String whyInterested;

    @Column(name = "why_good_fit", columnDefinition = "TEXT")
    private String whyGoodFit;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "opportunity_applicant_skills", joinColumns = @JoinColumn(name = "opportunity_applicant_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "skill", nullable = false)
    @Builder.Default
    private List<String> relevantSkills = new ArrayList<>();

    /** IDs into the applicant's own {@code UserExperience} rows — never a copy of the content itself. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "opportunity_applicant_experiences", joinColumns = @JoinColumn(name = "opportunity_applicant_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "experience_id", nullable = false)
    @Builder.Default
    private List<String> experienceIds = new ArrayList<>();

    /** IDs into the applicant's own {@code UserProject} rows — never a copy of the content itself. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "opportunity_applicant_projects", joinColumns = @JoinColumn(name = "opportunity_applicant_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "project_id", nullable = false)
    @Builder.Default
    private List<String> projectIds = new ArrayList<>();

    @Convert(converter = AvailabilityConverter.class)
    @Column(length = 20)
    private Availability availability;

    @Column(name = "expected_commitment", length = 100)
    private String expectedCommitment;

    @Column(name = "additional_message", columnDefinition = "TEXT")
    private String additionalMessage;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
