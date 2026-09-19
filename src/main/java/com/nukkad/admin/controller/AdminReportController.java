package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminReportDto;
import com.nukkad.admin.dto.ResolveReportRequest;
import com.nukkad.admin.service.AdminReportService;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
@SecurityRequirement(name = "bearerAuth")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminReportDto>> list(@RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String category,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(adminReportService.listReports(status, category, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminReportDto> get(@PathVariable String id) {
        return ApiResponse.ok(adminReportService.getReport(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AdminReportDto> resolve(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable String id,
                                                @Valid @RequestBody ResolveReportRequest request,
                                                HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminReportService.resolve(
                principal.id(), id, request.status(), request.resolutionNote(), httpRequest.getRemoteAddr()));
    }
}
