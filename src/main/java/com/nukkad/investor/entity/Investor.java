package com.nukkad.investor.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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

/**
 * An admin-managed investor catalog record — what founders see in Investor Discovery. This is deliberately a
 * separate entity from {@link InvestorProfile}: that one belongs to a real BuildAdda user who applied and was
 * activated (see {@link InvestorActivationRequest}), and stays exactly as it was for the two-way introduction
 * workflow those accounts already use. A catalog row here is never created by a user action — only an admin adds,
 * edits or retires one (see {@code com.nukkad.investor.service.InvestorCatalogService}).
 * <p>
 * {@link #linkedInvestorProfileId} is the one bridge between the two worlds: when an admin has tied a catalog row
 * to one of those live accounts, "Request introduction" on it reuses the existing {@code IntroRequest} pipeline
 * (notifications, conversations) unchanged. Left null — the common case, most catalog investors are firms that
 * aren't BuildAdda users at all — a request is instead recorded for an admin to action manually.
 */
@Entity
@Table(name = "investors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Investor {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    /** The source dataset's own id for this row (CSV column "id"), preserved so a later re-import of the same
     *  file updates this row instead of creating a duplicate — see InvestorImportService. Null for a
     *  hand-created row; unique when set (enforced by a DB index), never shown to a founder. */
    @Column(name = "external_source_id", length = 100)
    private String externalSourceId;

    @Column(nullable = false, length = 200)
    private String name;

    @Convert(converter = InvestorTypeConverter.class)
    @Column(name = "investor_type", nullable = false, length = 20)
    private InvestorType investorType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 200)
    private String location;

    @Column(length = 100)
    private String country;

    // Real-world exports occasionally carry a share-widget URL with an entire encoded page glued onto the
    // query string (see V92) instead of a plain link — 2048 is the conventional "URL max length" ceiling,
    // comfortably clear of anything realistic without resorting to an unbounded TEXT column.
    @Column(length = 2048)
    private String website;

    /** The bare domain (e.g. "peak.vc"), separate from {@link #website} — used to derive a best-effort logo
     *  fallback when no admin-uploaded one exists (see the frontend's investor-logo helper). */
    @Column(length = 2048)
    private String domain;

    /** A hosted image (uploaded by an admin); null falls back to a domain-derived logo, then an initials avatar. */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "investor_sectors", joinColumns = @JoinColumn(name = "investor_id"))
    @Column(name = "sector", nullable = false)
    @Builder.Default
    private Set<String> sectors = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "investor_stages", joinColumns = @JoinColumn(name = "investor_id"))
    @Column(name = "stage", nullable = false)
    @Builder.Default
    private Set<String> stages = new HashSet<>();

    /** Named programs this investor runs (e.g. an accelerator cohort) — only ever populated from real data
     *  (CSV "program" or admin entry), never inferred. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "investor_programs", joinColumns = @JoinColumn(name = "investor_id"))
    @Column(name = "program", nullable = false)
    @Builder.Default
    private Set<String> programs = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "investor_key_people", joinColumns = @JoinColumn(name = "investor_id"))
    @Column(name = "person", nullable = false)
    @Builder.Default
    private Set<String> keyPeople = new HashSet<>();

    @Column(name = "investment_count")
    private Integer investmentCount;

    @Column(name = "exit_count")
    private Integer exitCount;

    @Column(name = "cheque_min")
    private Long chequeMin;

    @Column(name = "cheque_max")
    private Long chequeMax;

    // ---- Admin-only contact details — InvestorMapper (founder-facing InvestorDto) never reads these. ----

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_email_verified")
    private Boolean contactEmailVerified;

    @Column(name = "secondary_email", length = 255)
    private String secondaryEmail;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    // See the comment on `website` above — the same real-world "share URL with an encoded page in the query
    // string" problem applies to every social link column, not just Twitter's (see V92).
    @Column(name = "facebook_url", length = 2048)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 2048)
    private String instagramUrl;

    @Column(name = "linkedin_url", length = 2048)
    private String linkedinUrl;

    @Column(name = "twitter_url", length = 2048)
    private String twitterUrl;

    /** Which import batch last created/updated the CSV-sourced fields on this row — null for a hand-created
     *  or never-reimported row. Traceability only; nothing reads this to decide behavior. */
    @Column(name = "source_batch_id", columnDefinition = "CHAR(36)")
    private String sourceBatchId;

    /** Off = hidden everywhere in Discovery, same as {@link #visible} false — the two are separate admin controls
     *  (spec: "Active/inactive" and "Visible/Hidden" are distinct toggles) even though founders see one outcome. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean visible = true;

    /** Internal only — never sent to a founder-facing response. See the class comment. */
    @Column(name = "linked_investor_profile_id", columnDefinition = "CHAR(36)")
    private String linkedInvestorProfileId;

    @Column(name = "created_by_admin_id", nullable = false, columnDefinition = "CHAR(36)")
    private String createdByAdminId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Whether this row shows up in founder-facing Discovery at all. */
    public boolean isPubliclyVisible() {
        return active && visible;
    }
}
