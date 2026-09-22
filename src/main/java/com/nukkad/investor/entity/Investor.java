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

    @Column(nullable = false, length = 200)
    private String name;

    @Convert(converter = InvestorTypeConverter.class)
    @Column(name = "investor_type", nullable = false, length = 20)
    private InvestorType investorType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 200)
    private String location;

    @Column(length = 300)
    private String website;

    /** A hosted image (uploaded by an admin); null falls back to an initials avatar. */
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

    @Column(name = "cheque_min")
    private Long chequeMin;

    @Column(name = "cheque_max")
    private Long chequeMax;

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
