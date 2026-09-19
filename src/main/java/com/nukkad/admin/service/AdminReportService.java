package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminReportDto;
import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.report.entity.Report;
import com.nukkad.report.entity.ReportStatus;
import com.nukkad.report.service.ReportService;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminReportService {

    private final ReportService reportService;
    private final UserRepository userRepository;
    private final AdminMapper adminMapper;
    private final AuditService auditService;

    // Deliberately has no access to message content: private conversations are never readable from
    // the admin side (a report only records THAT a conversation was reported, via its id).
    public AdminReportService(ReportService reportService, UserRepository userRepository,
                               AdminMapper adminMapper, AuditService auditService) {
        this.reportService = reportService;
        this.userRepository = userRepository;
        this.adminMapper = adminMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<AdminReportDto> listReports(String status, String category, int page, int size) {
        ReportStatus statusEnum = parseOptionalStatus(status);
        Page<Report> reports = reportService.listReports(statusEnum, category, page, AdminPaging.clampSize(size));
        Map<String, User> users = fetchReferencedUsers(reports.getContent());
        return reports.map(r -> adminMapper.toDto(r, users));
    }

    @Transactional(readOnly = true)
    public AdminReportDto getReport(String id) {
        Report report = reportService.getEntityOrThrow(id);
        Map<String, User> users = fetchReferencedUsers(java.util.List.of(report));
        return adminMapper.toDto(report, users);
    }

    @Transactional
    public AdminReportDto resolve(String adminId, String reportId, String status, String resolutionNote, String ip) {
        ReportStatus target;
        try {
            target = ReportStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + status);
        }
        if (target != ReportStatus.RESOLVED && target != ReportStatus.DISMISSED) {
            throw new BadRequestException("A report can only be resolved as RESOLVED or DISMISSED");
        }
        Report resolved = reportService.resolve(adminId, reportId, target, resolutionNote);
        auditService.log(adminId, AuditAction.ADMIN_REPORT_RESOLVED, "Report", reportId, ip,
                Map.of("status", target.name()));
        Map<String, User> users = fetchReferencedUsers(java.util.List.of(resolved));
        return adminMapper.toDto(resolved, users);
    }

    private ReportStatus parseOptionalStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return ReportStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + status);
        }
    }

    private Map<String, User> fetchReferencedUsers(java.util.List<Report> reports) {
        Set<String> ids = new HashSet<>();
        for (Report r : reports) {
            ids.add(r.getReporterId());
            ids.add(r.getReportedUserId());
            if (r.getResolvedByUserId() != null) ids.add(r.getResolvedByUserId());
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
    }
}
