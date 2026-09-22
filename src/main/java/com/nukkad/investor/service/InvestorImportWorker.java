package com.nukkad.investor.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.investor.entity.Investor;
import com.nukkad.investor.entity.InvestorImportBatch;
import com.nukkad.investor.entity.InvestorImportIssue;
import com.nukkad.investor.entity.InvestorImportIssueSeverity;
import com.nukkad.investor.entity.InvestorImportStatus;
import com.nukkad.investor.entity.InvestorType;
import com.nukkad.investor.repository.InvestorImportBatchRepository;
import com.nukkad.investor.repository.InvestorImportIssueRepository;
import com.nukkad.investor.repository.InvestorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Does the actual row-by-row work of a CSV import, off the HTTP request thread (see AsyncConfig). Split out
 * of {@link InvestorImportService} deliberately: {@code @Async} only takes effect on a call that crosses a
 * Spring proxy boundary, so {@link InvestorImportService#startImport} calling into a *different* bean here is
 * what makes this genuinely non-blocking — a same-class self-call would silently run synchronously instead.
 * <p>
 * No method here is wrapped in one long {@code @Transactional}: each repository call is independently
 * transactional (Spring Data JPA's default), which is deliberate — it's what lets the batch's progress
 * counters actually become visible to a concurrent admin poller (GET .../import/{id}) while a large file is
 * still processing, instead of only appearing once the whole import commits at the end.
 */
@Service
public class InvestorImportWorker {

    private static final Logger log = LoggerFactory.getLogger(InvestorImportWorker.class);
    private static final int PROGRESS_SAVE_EVERY = 50;

    private enum Outcome { CREATED, UPDATED, SKIPPED }

    private final InvestorRepository investorRepository;
    private final InvestorImportBatchRepository investorImportBatchRepository;
    private final InvestorImportIssueRepository investorImportIssueRepository;
    private final AuditService auditService;

    public InvestorImportWorker(InvestorRepository investorRepository,
                                 InvestorImportBatchRepository investorImportBatchRepository,
                                 InvestorImportIssueRepository investorImportIssueRepository,
                                 AuditService auditService) {
        this.investorRepository = investorRepository;
        this.investorImportBatchRepository = investorImportBatchRepository;
        this.investorImportIssueRepository = investorImportIssueRepository;
        this.auditService = auditService;
    }

    @Async("investorImportExecutor")
    public void processImportAsync(String batchId, List<InvestorCsvRow> rows, String adminId, String ip) {
        if (investorImportBatchRepository.findById(batchId).isEmpty()) {
            log.warn("Investor import batch {} vanished before processing started", batchId);
            return;
        }
        markProcessing(batchId);

        int created = 0, updated = 0, skipped = 0, failed = 0, processed = 0;
        InvestorImportStatus finalStatus = InvestorImportStatus.COMPLETED;
        String fatalMessage = null;
        try {
            for (InvestorCsvRow row : rows) {
                try {
                    switch (importRow(batchId, adminId, row)) {
                        case CREATED -> created++;
                        case UPDATED -> updated++;
                        case SKIPPED -> skipped++;
                    }
                } catch (Exception rowFailure) {
                    failed++;
                    log.warn("Investor import row {} in batch {} failed unexpectedly: {}", row.rowNumber(), batchId, rowFailure.getMessage());
                    saveIssueQuietly(batchId, row.rowNumber(), row.externalSourceId(), row.name(),
                            InvestorImportIssueSeverity.ERROR, "Unexpected error: " + rowFailure.getMessage());
                }
                processed++;
                if (processed % PROGRESS_SAVE_EVERY == 0) {
                    updateProgress(batchId, processed, created, updated, skipped, failed);
                }
            }
        } catch (Exception fatal) {
            log.error("Investor import batch {} aborted unexpectedly: {}", batchId, fatal.getMessage(), fatal);
            finalStatus = InvestorImportStatus.FAILED;
            fatalMessage = fatal.getMessage();
        }

        updateProgress(batchId, processed, created, updated, skipped, failed);
        finish(batchId, finalStatus, fatalMessage);
        auditService.log(adminId, AuditAction.ADMIN_INVESTOR_IMPORTED, "InvestorImportBatch", batchId, ip,
                Map.of("created", created, "updated", updated, "skipped", skipped, "failed", failed));
    }

    private Outcome importRow(String batchId, String adminId, InvestorCsvRow row) {
        if (row.hasHardError()) {
            saveIssueQuietly(batchId, row.rowNumber(), row.externalSourceId(), row.name(), InvestorImportIssueSeverity.ERROR, row.hardError());
            return Outcome.SKIPPED;
        }
        for (String warning : row.warnings()) {
            saveIssueQuietly(batchId, row.rowNumber(), row.externalSourceId(), row.name(), InvestorImportIssueSeverity.WARNING, warning);
        }

        InvestorType type = row.resolvedInvestorType() != null ? row.resolvedInvestorType() : InvestorType.OTHER;
        Investor existing = row.externalSourceId() != null
                ? investorRepository.findByExternalSourceId(row.externalSourceId()).orElse(null)
                : null;

        if (existing != null) {
            applyCsvFields(existing, row, type);
            existing.setSourceBatchId(batchId);
            investorRepository.saveAndFlush(existing);
            return Outcome.UPDATED;
        }

        Investor investor = Investor.builder()
                .externalSourceId(row.externalSourceId())
                .name(row.name())
                .investorType(type)
                .active(true)
                .visible(true)
                .createdByAdminId(adminId)
                .sourceBatchId(batchId)
                .build();
        applyCsvFields(investor, row, type);
        investorRepository.saveAndFlush(investor);
        return Outcome.CREATED;
    }

    /** Overwrites every CSV-owned field with this row's values (including clearing ones now blank — a
     *  re-import is a full sync of these columns), and never touches the admin-owned ones: active, visible,
     *  logoUrl, chequeMin/Max, stages, linkedInvestorProfileId. Those are enriched by hand and must survive
     *  a later re-import unchanged. */
    private void applyCsvFields(Investor target, InvestorCsvRow row, InvestorType type) {
        target.setName(row.name());
        target.setInvestorType(type);
        target.setDescription(row.description());
        target.setLocation(row.location());
        target.setCountry(row.country());
        target.setWebsite(row.website());
        target.setDomain(row.domain());
        target.setSectors(new HashSet<>(row.sectors()));
        target.setPrograms(new HashSet<>(row.programs()));
        target.setInvestmentCount(row.investmentCount());
        target.setExitCount(row.exitCount());
        target.setKeyPeople(new HashSet<>(row.keyPeople()));
        target.setFacebookUrl(row.facebookUrl());
        target.setInstagramUrl(row.instagramUrl());
        target.setLinkedinUrl(row.linkedinUrl());
        target.setTwitterUrl(row.twitterUrl());
        target.setContactEmail(row.contactEmail());
        target.setContactEmailVerified(row.contactEmailVerified());
        target.setSecondaryEmail(row.secondaryEmail());
        target.setPhoneNumber(row.phoneNumber());
    }

    private void markProcessing(String batchId) {
        investorImportBatchRepository.findById(batchId).ifPresent(b -> {
            b.setStatus(InvestorImportStatus.PROCESSING);
            b.setStartedAt(Instant.now());
            investorImportBatchRepository.save(b);
        });
    }

    private void updateProgress(String batchId, int processed, int created, int updated, int skipped, int failed) {
        investorImportBatchRepository.findById(batchId).ifPresent(b -> {
            b.setProcessedRows(processed);
            b.setCreatedCount(created);
            b.setUpdatedCount(updated);
            b.setSkippedCount(skipped);
            b.setFailedCount(failed);
            investorImportBatchRepository.save(b);
        });
    }

    private void finish(String batchId, InvestorImportStatus status, String errorMessage) {
        investorImportBatchRepository.findById(batchId).ifPresent(b -> {
            b.setStatus(status);
            b.setErrorMessage(errorMessage);
            b.setCompletedAt(Instant.now());
            investorImportBatchRepository.save(b);
        });
    }

    private void saveIssueQuietly(String batchId, int rowNumber, String externalSourceId, String investorName,
                                   InvestorImportIssueSeverity severity, String message) {
        String trimmed = message != null && message.length() > 500 ? message.substring(0, 500) : message;
        try {
            investorImportIssueRepository.save(InvestorImportIssue.builder()
                    .batchId(batchId)
                    .rowNumber(rowNumber)
                    .externalSourceId(externalSourceId)
                    .investorName(investorName)
                    .severity(severity)
                    .message(trimmed)
                    .build());
        } catch (Exception e) {
            log.warn("Could not record investor import issue for batch {} row {}: {}", batchId, rowNumber, e.getMessage());
        }
    }
}
