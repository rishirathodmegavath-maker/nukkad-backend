package com.nukkad.program.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A BuildAdda startup program (SPARK, IGNITE, and whatever Admin creates after them) — content and
 * operational settings together, admin-managed like every other content type (Resources, Startups,
 * Grants). Previously this was a fixed two-value Java enum with its content hardcoded in a
 * {@code ProgramCatalog} class; that stopped being sufficient once Admin needed to create and edit
 * programs, since a closed enum can't grow at runtime. {@code slug} is what a
 * {@link ProgramApplication} row and legacy URLs identify a program by — for the two programs that
 * existed before this migration it is literally {@code "SPARK"} / {@code "IGNITE"}, unchanged from
 * the old enum's {@code name()}, so existing application rows keep resolving with no data migration
 * on {@code program_applications} at all.
 * <p>
 * {@code journeyJson} / {@code benefitsJson} / {@code applicationStepsJson} hold structured content
 * (see {@link com.nukkad.program.catalog.ProgramContentCodec}) as JSON text rather than child tables:
 * each is always authored and read as one whole unit — the admin form's journey/benefits/application
 * builder — never queried or joined on field-by-field, which is exactly the case the codebase's
 * existing {@code AuditLog.details} JSON-text column already covers. This also means none of them is
 * a lazy collection, so the "mapper must copy before the transaction closes" bug class (see
 * ChapterMapper/ProgramMapper's own fix earlier) cannot occur here — a JSON text column is always
 * loaded eagerly, like any other scalar.
 */
@Entity
@Table(name = "programs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    /** Matches {@link ProgramApplication#getProgram()} by exact string equality — see class doc. */
    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    /** Short label/eyebrow shown on the card (e.g. "EXPLORE"). Optional. */
    @Column(length = 40)
    private String badge;

    /** The short, one-line description ("tagline" in the public DTO). */
    @Column(nullable = false, length = 220)
    private String tagline;

    /** The longer description shown on the detail page. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "hero_image_url", length = 500)
    private String heroImageUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProgramStatus status = ProgramStatus.DRAFT;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "outcome_heading", length = 200)
    private String outcomeHeading;

    @Column(name = "outcome_description", columnDefinition = "TEXT")
    private String outcomeDescription;

    @Column(name = "audience_description", length = 500)
    private String audienceDescription;

    @Column(name = "eligibility_title", length = 200)
    private String eligibilityTitle;

    @Column(name = "eligibility_description", length = 500)
    private String eligibilityDescription;

    /** Operational settings, folded in from the old separate {@code program_settings} table — a
     *  program's content and its "is it currently accepting applications" state are edited together
     *  in the same admin form, so there is no longer a reason to keep them in two tables. Every
     *  value defaults to "not yet defined" (null / applicationOpen=true) rather than a fabricated
     *  number, per the product spec's explicit instruction not to invent fees or claims. */
    @Column(name = "application_open", nullable = false)
    @Builder.Default
    private boolean applicationOpen = true;

    @Column(name = "fee_amount")
    private Integer feeAmount;

    @Column(name = "fee_currency", length = 10)
    private String feeCurrency;

    @Column(name = "enrollment_info", length = 500)
    private String enrollmentInfo;

    /** Null means "not stated" — distinct from false, which would be an actual claim that the
     *  process isn't selective. */
    private Boolean selective;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "highlights_json", columnDefinition = "JSON")
    private String highlightsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_audience_json", columnDefinition = "JSON")
    private String targetAudienceJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_points_json", columnDefinition = "JSON")
    private String eligibilityPointsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "journey_json", columnDefinition = "JSON")
    private String journeyJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefits_json", columnDefinition = "JSON")
    private String benefitsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "application_steps_json", columnDefinition = "JSON")
    private String applicationStepsJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
