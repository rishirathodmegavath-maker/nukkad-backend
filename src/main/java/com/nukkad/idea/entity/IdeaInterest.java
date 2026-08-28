package com.nukkad.idea.entity;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "idea_interests", uniqueConstraints = @UniqueConstraint(name = "uq_idea_interest", columnNames = {"idea_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdeaInterest {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "idea_id", nullable = false, columnDefinition = "CHAR(36)")
    private String ideaId;

    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "idea_interest_contribution_areas", joinColumns = @JoinColumn(name = "idea_interest_id"))
    @Convert(converter = ContributionAreaConverter.class)
    @Column(name = "contribution_area", nullable = false)
    @Builder.Default
    private Set<ContributionArea> contributionAreas = new HashSet<>();

    @Convert(converter = IdeaInterestStatusConverter.class)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IdeaInterestStatus status = IdeaInterestStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String message;

    /** Skills the applicant chose to highlight for this idea — defaults to their profile skills, editable. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "idea_interest_skills", joinColumns = @JoinColumn(name = "idea_interest_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "skill", nullable = false)
    @Builder.Default
    private List<String> relevantSkills = new ArrayList<>();

    /** IDs into the applicant's own {@code UserExperience} rows — never a copy of the content itself. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "idea_interest_experiences", joinColumns = @JoinColumn(name = "idea_interest_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "experience_id", nullable = false)
    @Builder.Default
    private List<String> experienceIds = new ArrayList<>();

    /** IDs into the applicant's own {@code UserProject} rows — never a copy of the content itself. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "idea_interest_projects", joinColumns = @JoinColumn(name = "idea_interest_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "project_id", nullable = false)
    @Builder.Default
    private List<String> projectIds = new ArrayList<>();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
