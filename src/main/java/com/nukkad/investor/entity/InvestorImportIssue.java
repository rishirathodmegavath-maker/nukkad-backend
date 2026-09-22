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

/** One row of a CSV import that wasn't a clean success — see {@link InvestorImportBatch}. */
@Entity
@Table(name = "investor_import_issues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestorImportIssue {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "batch_id", nullable = false, columnDefinition = "CHAR(36)")
    private String batchId;

    /** 1-based position in the CSV (header row excluded), so an admin can find it in the original file. */
    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "external_source_id", length = 100)
    private String externalSourceId;

    @Column(name = "investor_name", length = 200)
    private String investorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvestorImportIssueSeverity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
