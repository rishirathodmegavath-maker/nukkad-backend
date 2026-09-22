package com.nukkad.investor.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.investor.dto.InvestorImportBatchDto;
import com.nukkad.investor.dto.InvestorImportIssueDto;
import com.nukkad.investor.dto.InvestorImportPreviewDto;
import com.nukkad.investor.dto.InvestorImportPreviewRowDto;
import com.nukkad.investor.entity.InvestorImportBatch;
import com.nukkad.investor.entity.InvestorImportIssue;
import com.nukkad.investor.entity.InvestorImportStatus;
import com.nukkad.investor.repository.InvestorImportBatchRepository;
import com.nukkad.investor.repository.InvestorImportIssueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * The admin investor CSV bulk-import pipeline's request-facing half: parse-and-preview (nothing saved),
 * then confirm-and-start (kicks off {@link InvestorImportWorker} off-thread and returns immediately), plus
 * polling/listing the resulting {@link InvestorImportBatch} rows. See {@code AdminInvestorCatalogController}
 * for the endpoints and {@link InvestorImportWorker} for the actual row-by-row upsert logic.
 */
@Service
public class InvestorImportService {

    private static final int PREVIEW_SAMPLE_SIZE = 10;

    private final InvestorCsvParser investorCsvParser;
    private final InvestorImportBatchRepository investorImportBatchRepository;
    private final InvestorImportIssueRepository investorImportIssueRepository;
    private final InvestorImportWorker investorImportWorker;
    private final FileStorageService fileStorageService;

    public InvestorImportService(InvestorCsvParser investorCsvParser,
                                  InvestorImportBatchRepository investorImportBatchRepository,
                                  InvestorImportIssueRepository investorImportIssueRepository,
                                  InvestorImportWorker investorImportWorker,
                                  FileStorageService fileStorageService) {
        this.investorCsvParser = investorCsvParser;
        this.investorImportBatchRepository = investorImportBatchRepository;
        this.investorImportIssueRepository = investorImportIssueRepository;
        this.investorImportWorker = investorImportWorker;
        this.fileStorageService = fileStorageService;
    }

    /** Parses and validates the whole file but saves nothing — lets the admin see column detection, row
     *  count and a sample before committing to an import. */
    public InvestorImportPreviewDto preview(MultipartFile file) {
        InvestorCsvParseResult result = parse(file);
        List<InvestorImportPreviewRowDto> sample = result.rows().stream()
                .limit(PREVIEW_SAMPLE_SIZE)
                .map(row -> new InvestorImportPreviewRowDto(
                        row.rowNumber(),
                        row.externalSourceId(),
                        row.name(),
                        row.resolvedInvestorType() != null
                                ? row.resolvedInvestorType().getLabel()
                                : (row.rawInvestorType() != null ? row.rawInvestorType() + " (unrecognized → Other)" : null),
                        row.location(),
                        row.country(),
                        row.warnings(),
                        row.hardError()))
                .toList();
        return new InvestorImportPreviewDto(result.rows().size(), result.headers(), result.unrecognizedHeaders(), result.hasIdColumn(), result.note(), sample);
    }

    /** Parses the file again (the browser re-sends it — see the controller), stores it for audit/re-download,
     *  records a PENDING batch, and hands the parsed rows to {@link InvestorImportWorker} to process off the
     *  request thread. Deliberately NOT @Transactional: the batch row must be committed (and so visible to
     *  the async worker's own, separate transaction) before that worker call is made — wrapping this method
     *  in one transaction would let the async call race ahead of the commit. */
    public InvestorImportBatchDto startImport(String adminId, MultipartFile file, String ip) {
        InvestorCsvParseResult result = parse(file);
        if (result.rows().isEmpty()) {
            throw new BadRequestException("This file has no data rows to import");
        }

        String sourceFileUrl;
        try {
            sourceFileUrl = fileStorageService.storeResourceFile(file, "investor-imports");
        } catch (RuntimeException e) {
            // Best-effort audit copy — losing it must never block the import itself.
            sourceFileUrl = null;
        }

        InvestorImportBatch batch = InvestorImportBatch.builder()
                .adminId(adminId)
                .originalFilename(file.getOriginalFilename())
                .sourceFileUrl(sourceFileUrl)
                .status(InvestorImportStatus.PENDING)
                .totalRows(result.rows().size())
                .build();
        batch = investorImportBatchRepository.saveAndFlush(batch);

        investorImportWorker.processImportAsync(batch.getId(), result.rows(), adminId, ip);

        return toDto(batch);
    }

    @Transactional(readOnly = true)
    public InvestorImportBatchDto getBatch(String id) {
        return toDto(investorImportBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found: " + id)));
    }

    @Transactional(readOnly = true)
    public Page<InvestorImportBatchDto> listBatches(int page, int size) {
        return investorImportBatchRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<InvestorImportIssueDto> listIssues(String batchId, int page, int size) {
        if (!investorImportBatchRepository.existsById(batchId)) {
            throw new ResourceNotFoundException("Import batch not found: " + batchId);
        }
        Page<InvestorImportIssue> issues = investorImportIssueRepository.findByBatchIdOrderByRowNumberAsc(
                batchId, PageRequest.of(page, size, Sort.by("rowNumber")));
        return issues.map(this::toDto);
    }

    private InvestorCsvParseResult parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        try {
            return isExcel(file) ? investorCsvParser.parseExcel(file.getInputStream()) : investorCsvParser.parse(file.getInputStream());
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }
    }

    /** Excel workbooks are binary (a zip container) — routed to {@link InvestorCsvParser#parseExcel} instead
     *  of the CSV text parser, which would otherwise just fail on the raw bytes. Checked by extension first
     *  since browsers are inconsistent about the content-type they attach to a file input. */
    private static boolean isExcel(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase().endsWith(".xlsx")) return true;
        String contentType = file.getContentType();
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType);
    }

    private InvestorImportBatchDto toDto(InvestorImportBatch b) {
        return new InvestorImportBatchDto(b.getId(), b.getOriginalFilename(), b.getStatus().name(), b.getTotalRows(),
                b.getProcessedRows(), b.getCreatedCount(), b.getUpdatedCount(), b.getSkippedCount(), b.getFailedCount(),
                b.getErrorMessage(), b.getStartedAt(), b.getCompletedAt(), b.getCreatedAt());
    }

    private InvestorImportIssueDto toDto(InvestorImportIssue i) {
        return new InvestorImportIssueDto(i.getId(), i.getRowNumber(), i.getExternalSourceId(), i.getInvestorName(),
                i.getSeverity().name(), i.getMessage(), i.getCreatedAt());
    }
}
