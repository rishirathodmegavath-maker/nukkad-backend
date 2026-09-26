package com.nukkad.program.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
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
import java.util.HashMap;
import java.util.Map;

/**
 * One applicant's application to one program. {@code answers} is a flexible key→value map
 * (field key from {@link com.nukkad.program.catalog.ProgramField#key()} → the applicant's
 * text) rather than a fixed column per question: SPARK and IGNITE have entirely different
 * question sets, and either program's questions can be relabelled without a migration since the
 * key, not the column, is what's stored. A multiselect answer (e.g. "interests") is stored as its
 * options joined with "|" under a single key — there are few enough multiselect fields that a
 * second child table would be overkill.
 * <p>
 * Deliberately no unique constraint on (applicantUserId, program): an applicant may have more than
 * one row over time (e.g. a fresh application after a REJECTED one), and {@link
 * com.nukkad.program.service.ProgramApplicationService} — not the schema — is what enforces at
 * most one non-terminal row at a time.
 */
@Entity
@Table(name = "program_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramApplication {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "applicant_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String applicantUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Program program;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProgramApplicationStatus status = ProgramApplicationStatus.DRAFT;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "program_application_answers", joinColumns = @JoinColumn(name = "application_id"))
    @MapKeyColumn(name = "field_key")
    @Column(name = "field_value", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, String> answers = new HashMap<>();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Never exposed to the applicant — see ProgramApplicationDto vs AdminProgramApplicationDto. */
    @Column(name = "admin_note", length = 1000)
    private String adminNote;

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
