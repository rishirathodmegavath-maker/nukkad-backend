package com.nukkad.grant.entity;

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

/** One row of a spreadsheet import that wasn't a clean success — see {@link GrantImportBatch}. */
@Entity
@Table(name = "grant_import_issues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrantImportIssue {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "batch_id", nullable = false, columnDefinition = "CHAR(36)")
    private String batchId;

    /** 1-based position in the sheet (header row excluded), so an admin can find it in the
     *  original file. Column is "row_num", not "row_number" -- MySQL 8.0+ reserves ROW_NUMBER
     *  (the window function). */
    @Column(name = "row_num", nullable = false)
    private int rowNumber;

    @Column(name = "grant_name", length = 200)
    private String grantName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GrantImportIssueSeverity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
