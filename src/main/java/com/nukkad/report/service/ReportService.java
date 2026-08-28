package com.nukkad.report.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.report.entity.Report;
import com.nukkad.report.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @Transactional
    public void submit(String reporterId, String reportedUserId, String category, String conversationId) {
        if (reporterId.equals(reportedUserId)) throw new BadRequestException("Cannot report yourself");
        Report report = Report.builder()
                .reporterId(reporterId)
                .reportedUserId(reportedUserId)
                .category(category)
                .conversationId(conversationId)
                .build();
        reportRepository.save(report);
    }
}
