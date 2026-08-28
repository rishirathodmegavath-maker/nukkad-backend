package com.nukkad.report.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.report.dto.SubmitReportRequest;
import com.nukkad.report.service.ReportService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ApiResponse<Void> submit(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody SubmitReportRequest request) {
        reportService.submit(principal.id(), request.reportedUserId(), request.category(), request.conversationId());
        return ApiResponse.ok(null);
    }
}
