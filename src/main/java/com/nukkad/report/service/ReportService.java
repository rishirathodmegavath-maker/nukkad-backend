package com.nukkad.report.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.paging.PageRequests;
import com.nukkad.feed.service.FeedService;
import com.nukkad.report.entity.Report;
import com.nukkad.report.entity.ReportStatus;
import com.nukkad.report.repository.ReportRepository;
import com.nukkad.report.repository.ReportSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final FeedService feedService;

    public ReportService(ReportRepository reportRepository, FeedService feedService) {
        this.reportRepository = reportRepository;
        this.feedService = feedService;
    }

    // reportedUserId is resolved from the post's own author when postId is given — never trusted
    // from the client directly in that case, so a caller can't report post X while attributing it
    // to an unrelated user Y. Going through FeedService.requireVisibleAuthorId (rather than looking
    // the post up directly) also means a postId the reporter can't actually see behaves exactly like
    // a nonexistent one — the same 404 either way — instead of letting "report" be used to probe
    // whether a connections-only post from a stranger exists.
    @Transactional
    public void submit(String reporterId, String reportedUserId, String category, String conversationId, String postId) {
        String resolvedReportedUserId = reportedUserId;
        if (postId != null && !postId.isBlank()) {
            resolvedReportedUserId = feedService.requireVisibleAuthorId(reporterId, postId);
        }
        if (resolvedReportedUserId == null || resolvedReportedUserId.isBlank()) {
            throw new BadRequestException("reportedUserId or postId is required");
        }
        if (reporterId.equals(resolvedReportedUserId)) throw new BadRequestException("Cannot report yourself");
        // Idempotent, not rejected: a second click/retry on the same target while the first report
        // is still OPEN adds no new signal for the moderation queue, so it's a silent no-op rather
        // than an error the caller would have to handle specially.
        String targetUserId = resolvedReportedUserId;
        boolean alreadyOpen = reportRepository.findByReporterIdAndStatus(reporterId, ReportStatus.OPEN).stream()
                .anyMatch(r -> postId != null && !postId.isBlank() ? postId.equals(r.getPostId())
                        : r.getPostId() == null && targetUserId.equals(r.getReportedUserId()));
        if (alreadyOpen) return;
        Report report = Report.builder()
                .reporterId(reporterId)
                .reportedUserId(resolvedReportedUserId)
                .category(category)
                .conversationId(conversationId)
                .postId(postId)
                .build();
        reportRepository.save(report);
    }

    public Report getEntityOrThrow(String id) {
        return reportRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Report> listReports(ReportStatus status, String category, int page, int size) {
        Specification<Report> spec = ReportSpecifications.combine(
                ReportSpecifications.status(status),
                ReportSpecifications.category(category)
        );
        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return reportRepository.findAll(spec, pageable);
    }

    /** Admin-only transition: a report may be reviewed exactly once (OPEN -> RESOLVED/DISMISSED);
     *  repeat submissions of the same resolve action are rejected rather than silently reapplied,
     *  so a double-click can't produce contradictory resolver/timestamp state. */
    @Transactional
    public Report resolve(String adminId, String reportId, ReportStatus newStatus, String resolutionNote) {
        // Locked read: without this, two concurrent resolve calls on the same OPEN report can both
        // pass the OPEN check before either commits, so both succeed instead of one winning and the
        // other correctly hitting the "already reviewed" conflict below.
        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ConflictException("This report has already been reviewed");
        }
        report.setStatus(newStatus);
        report.setResolvedByUserId(adminId);
        report.setResolvedAt(Instant.now());
        report.setResolutionNote(resolutionNote);
        return reportRepository.saveAndFlush(report);
    }

    public long countByStatus(ReportStatus status) {
        return reportRepository.countByStatus(status);
    }
}
