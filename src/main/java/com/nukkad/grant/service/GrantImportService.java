package com.nukkad.grant.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.grant.dto.GrantImportBatchDto;
import com.nukkad.grant.dto.GrantImportIssueDto;
import com.nukkad.grant.dto.GrantImportPreviewDto;
import com.nukkad.grant.dto.GrantImportPreviewRowDto;
import com.nukkad.grant.entity.GrantImportBatch;
import com.nukkad.grant.entity.GrantImportIssue;
import com.nukkad.grant.entity.GrantImportStatus;
import com.nukkad.grant.repository.GrantImportBatchRepository;
import com.nukkad.grant.repository.GrantImportIssueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/**
 * The admin grants bulk-import pipeline's request-facing half: parse-and-preview (nothing saved),
 * then confirm-and-start (kicks off {@link GrantImportWorker} off-thread and returns immediately),
 * plus polling/listing the resulting {@link GrantImportBatch} rows. Mirrors
 * {@code com.nukkad.investor.service.InvestorImportService} exactly. This is the manual, zero-API-cost
 * replacement for the (currently disabled) Gemini-based grant discovery pipeline.
 */
@Service
public class GrantImportService {

    private static final int PREVIEW_SAMPLE_SIZE = 10;
    private static final DateTimeFormatter DEADLINE_DISPLAY = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private final GrantCsvParser grantCsvParser;
    private final GrantImportBatchRepository grantImportBatchRepository;
    private final GrantImportIssueRepository grantImportIssueRepository;
    private final GrantImportWorker grantImportWorker;

    public GrantImportService(GrantCsvParser grantCsvParser,
                               GrantImportBatchRepository grantImportBatchRepository,
                               GrantImportIssueRepository grantImportIssueRepository,
                               GrantImportWorker grantImportWorker) {
        this.grantCsvParser = grantCsvParser;
        this.grantImportBatchRepository = grantImportBatchRepository;
        this.grantImportIssueRepository = grantImportIssueRepository;
        this.grantImportWorker = grantImportWorker;
    }

    /** Parses and validates the whole file but saves nothing — lets the admin see column
     *  detection, row count and a sample before committing to an import. */
    public GrantImportPreviewDto preview(MultipartFile file) {
        GrantCsvParseResult result = parse(file);
        List<GrantImportPreviewRowDto> sample = result.rows().stream()
                .limit(PREVIEW_SAMPLE_SIZE)
                .map(row -> new GrantImportPreviewRowDto(
                        row.rowNumber(),
                        row.name(),
                        row.provider(),
                        row.resolvedProviderType() != null ? row.resolvedProviderType().getLabel() : null,
                        row.deadline() != null ? DEADLINE_DISPLAY.format(row.deadline().atZone(java.time.ZoneOffset.UTC)) : row.rawDeadline(),
                        row.warnings(),
                        row.hardError()))
                .toList();
        return new GrantImportPreviewDto(result.rows().size(), result.headers(), result.unrecognizedHeaders(), result.note(), sample);
    }

    /** Parses the file again (the browser re-sends it — see the controller), records a PENDING
     *  batch, and hands the parsed rows to {@link GrantImportWorker} to process off the request
     *  thread. Deliberately NOT @Transactional — see InvestorImportService#startImport's javadoc
     *  for why: the batch row must be committed before the async worker's own transaction reads it. */
    public GrantImportBatchDto startImport(String adminId, MultipartFile file) {
        GrantCsvParseResult result = parse(file);
        if (result.rows().isEmpty()) {
            throw new BadRequestException("This file has no data rows to import");
        }

        GrantImportBatch batch = GrantImportBatch.builder()
                .adminId(adminId)
                .originalFilename(file.getOriginalFilename())
                .status(GrantImportStatus.PENDING)
                .totalRows(result.rows().size())
                .build();
        batch = grantImportBatchRepository.saveAndFlush(batch);

        grantImportWorker.processImportAsync(batch.getId(), result.rows(), adminId);

        return toDto(batch);
    }

    @Transactional(readOnly = true)
    public GrantImportBatchDto getBatch(String id) {
        return toDto(grantImportBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found: " + id)));
    }

    @Transactional(readOnly = true)
    public Page<GrantImportBatchDto> listBatches(int page, int size) {
        return grantImportBatchRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<GrantImportIssueDto> listIssues(String batchId, int page, int size) {
        if (!grantImportBatchRepository.existsById(batchId)) {
            throw new ResourceNotFoundException("Import batch not found: " + batchId);
        }
        Page<GrantImportIssue> issues = grantImportIssueRepository.findByBatchIdOrderByRowNumberAsc(
                batchId, PageRequest.of(page, size, Sort.by("rowNumber")));
        return issues.map(this::toDto);
    }

    private GrantCsvParseResult parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        try {
            return isExcel(file) ? grantCsvParser.parseExcel(file.getInputStream()) : grantCsvParser.parse(file.getInputStream());
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }
    }

    private static boolean isExcel(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase().endsWith(".xlsx")) return true;
        String contentType = file.getContentType();
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType);
    }

    private GrantImportBatchDto toDto(GrantImportBatch b) {
        return new GrantImportBatchDto(b.getId(), b.getOriginalFilename(), b.getStatus().name(), b.getTotalRows(),
                b.getProcessedRows(), b.getCreatedCount(), b.getSkippedCount(), b.getFailedCount(),
                b.getErrorMessage(), b.getStartedAt(), b.getCompletedAt(), b.getCreatedAt());
    }

    private GrantImportIssueDto toDto(GrantImportIssue i) {
        return new GrantImportIssueDto(i.getId(), i.getRowNumber(), i.getGrantName(), i.getSeverity().name(), i.getMessage(), i.getCreatedAt());
    }
}
