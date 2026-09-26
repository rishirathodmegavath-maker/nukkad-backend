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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** The operational side of a program that genuinely varies and isn't in {@link
 *  com.nukkad.program.catalog.ProgramCatalog}'s fixed copy — whether it's currently accepting
 *  applications, its fee (if any), enrollment info, whether the process is selective. Admin-
 *  editable; every value defaults to "not yet defined" (null / applicationOpen=true) rather than a
 *  fabricated number, per the product spec's explicit instruction not to invent fees or claims. */
@Entity
@Table(name = "program_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramSettings {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Program program;

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

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
