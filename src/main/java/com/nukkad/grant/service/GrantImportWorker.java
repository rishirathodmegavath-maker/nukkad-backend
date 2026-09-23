package com.nukkad.grant.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.grant.entity.Grant;
import com.nukkad.grant.entity.GrantImportBatch;
import com.nukkad.grant.entity.GrantImportIssue;
import com.nukkad.grant.entity.GrantImportIssueSeverity;
import com.nukkad.grant.entity.GrantImportStatus;
import com.nukkad.grant.repository.GrantImportBatchRepository;
import com.nukkad.grant.repository.GrantImportIssueRepository;
import com.nukkad.grant.repository.GrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Does the actual row-by-row work of a grants spreadsheet import, off the HTTP request thread
 * (see AsyncConfig's {@code grantImportExecutor}). Split out of {@link GrantImportService}
 * deliberately — same reason as {@code com.nukkad.investor.service.InvestorImportWorker}:
 * {@code @Async} only takes effect across a Spring proxy boundary, so a same-class self-call
 * would silently run synchronously instead.
 * <p>
 * Every row always creates a new Grant — unlike the investor importer, grants have no natural
 * external-id column to dedupe/update by, so a re-upload of the same file simply creates
 * duplicates (the preview step is where an admin catches that before confirming).
 * Live immediately (moderationStatus APPROVED, discoveryOrigin MANUAL), matching how every other
 * admin-authored grant already behaves.
 */
@Service
public class GrantImportWorker {

    private static final Logger log = LoggerFactory.getLogger(GrantImportWorker.class);
    private static final int PROGRESS_SAVE_EVERY = 20;

    private final GrantRepository grantRepository;
    private final GrantImportBatchRepository grantImportBatchRepository;
    private final GrantImportIssueRepository grantImportIssueRepository;
    private final AuditService auditService;

    public GrantImportWorker(GrantRepository grantRepository,
                              GrantImportBatchRepository grantImportBatchRepository,
                              GrantImportIssueRepository grantImportIssueRepository,
                              AuditService auditService) {
        this.grantRepository = grantRepository;
        this.grantImportBatchRepository = grantImportBatchRepository;
        this.grantImportIssueRepository = grantImportIssueRepository;
        this.auditService = auditService;
    }

    @Async("grantImportExecutor")
    public void processImportAsync(String batchId, List<GrantCsvRow> rows, String adminId) {
        if (grantImportBatchRepository.findById(batchId).isEmpty()) {
            log.warn("Grant import batch {} vanished before processing started", batchId);
            return;
        }
        markProcessing(batchId);

        int created = 0, skipped = 0, failed = 0, processed = 0;
        GrantImportStatus finalStatus = GrantImportStatus.COMPLETED;
        String fatalMessage = null;
        try {
            for (GrantCsvRow row : rows) {
                try {
                    if (importRow(adminId, batchId, row)) created++;
                    else skipped++;
                } catch (Exception rowFailure) {
                    failed++;
                    log.warn("Grant import row {} in batch {} failed unexpectedly: {}", row.rowNumber(), batchId, rowFailure.getMessage());
                    saveIssueQuietly(batchId, row.rowNumber(), row.name(), GrantImportIssueSeverity.ERROR, "Unexpected error: " + rowFailure.getMessage());
                }
                processed++;
                if (processed % PROGRESS_SAVE_EVERY == 0) {
                    updateProgress(batchId, processed, created, skipped, failed);
                }
            }
        } catch (Exception fatal) {
            log.error("Grant import batch {} aborted unexpectedly: {}", batchId, fatal.getMessage(), fatal);
            finalStatus = GrantImportStatus.FAILED;
            fatalMessage = fatal.getMessage();
        }

        updateProgress(batchId, processed, created, skipped, failed);
        finish(batchId, finalStatus, fatalMessage);
        auditService.log(adminId, AuditAction.ADMIN_GRANT_IMPORTED, "GrantImportBatch", batchId, "internal:grant-import",
                Map.of("created", created, "skipped", skipped, "failed", failed));
    }

    /** @return true if a Grant was created, false if the row was skipped (hard error). */
    private boolean importRow(String adminId, String batchId, GrantCsvRow row) {
        if (row.hasHardError()) {
            saveIssueQuietly(batchId, row.rowNumber(), row.name(), GrantImportIssueSeverity.ERROR, row.hardError());
            return false;
        }
        for (String warning : row.warnings()) {
            saveIssueQuietly(batchId, row.rowNumber(), row.name(), GrantImportIssueSeverity.WARNING, warning);
        }

        Grant grant = Grant.builder()
                .name(row.name())
                .provider(row.provider())
                .providerType(row.resolvedProviderType())
                .description(row.description())
                .fundingAmount(row.fundingAmount())
                .eligibilityCriteria(row.eligibilityCriteria())
                .eligibleSectors(new HashSet<>(row.eligibleSectors()))
                .eligibleStages(new HashSet<>(row.eligibleStages()))
                .deadline(row.deadline())
                .applicationUrl(row.applicationUrl())
                .createdByUserId(adminId)
                .moderationStatus(ModerationStatus.APPROVED)
                .build();
        grantRepository.saveAndFlush(grant);
        return true;
    }

    private void markProcessing(String batchId) {
        grantImportBatchRepository.findById(batchId).ifPresent(b -> {
            b.setStatus(GrantImportStatus.PROCESSING);
            b.setStartedAt(Instant.now());
            grantImportBatchRepository.save(b);
        });
    }

    private void updateProgress(String batchId, int processed, int created, int skipped, int failed) {
        grantImportBatchRepository.findById(batchId).ifPresent(b -> {
            b.setProcessedRows(processed);
            b.setCreatedCount(created);
            b.setSkippedCount(skipped);
            b.setFailedCount(failed);
            grantImportBatchRepository.save(b);
        });
    }

    private void finish(String batchId, GrantImportStatus status, String errorMessage) {
        grantImportBatchRepository.findById(batchId).ifPresent(b -> {
            b.setStatus(status);
            b.setErrorMessage(errorMessage);
            b.setCompletedAt(Instant.now());
            grantImportBatchRepository.save(b);
        });
    }

    private void saveIssueQuietly(String batchId, int rowNumber, String grantName, GrantImportIssueSeverity severity, String message) {
        String trimmed = message != null && message.length() > 500 ? message.substring(0, 500) : message;
        try {
            grantImportIssueRepository.save(GrantImportIssue.builder()
                    .batchId(batchId)
                    .rowNumber(rowNumber)
                    .grantName(grantName)
                    .severity(severity)
                    .message(trimmed)
                    .build());
        } catch (Exception e) {
            log.warn("Could not record grant import issue for batch {} row {}: {}", batchId, rowNumber, e.getMessage());
        }
    }
}
