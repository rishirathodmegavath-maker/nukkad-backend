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
 * One admin CSV upload into the investor catalog. Created (status PENDING) synchronously when the admin
 * confirms the import, then processed off-request by {@code InvestorImportService#processImportAsync} — the
 * admin UI polls this row (GET /api/admin/investor-catalog/import/{id}) to show progress and, once COMPLETED
 * or FAILED, the final report. See {@link InvestorImportIssue} for the per-row exception log.
 */
@Entity
@Table(name = "investor_import_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestorImportBatch {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "admin_id", nullable = false, columnDefinition = "CHAR(36)")
    private String adminId;

    @Column(name = "original_filename")
    private String originalFilename;

    /** The exact uploaded file, stored via FileStorageService — null only if storage failed but the import
     *  was allowed to proceed anyway. */
    @Column(name = "source_file_url", length = 500)
    private String sourceFileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvestorImportStatus status = InvestorImportStatus.PENDING;

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private int totalRows = 0;

    @Column(name = "processed_rows", nullable = false)
    @Builder.Default
    private int processedRows = 0;

    @Column(name = "created_count", nullable = false)
    @Builder.Default
    private int createdCount = 0;

    @Column(name = "updated_count", nullable = false)
    @Builder.Default
    private int updatedCount = 0;

    @Column(name = "skipped_count", nullable = false)
    @Builder.Default
    private int skippedCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;

    /** Set only when the whole batch aborted from an unexpected error (status FAILED) — a per-row problem
     *  goes to {@link InvestorImportIssue} instead and never stops the rest of the file. */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
