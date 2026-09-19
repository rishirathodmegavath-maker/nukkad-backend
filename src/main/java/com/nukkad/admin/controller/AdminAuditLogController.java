package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminAuditLogDto;
import com.nukkad.admin.service.AdminAuditLogService;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Read-only — audit history has no mutation endpoint anywhere in this controller or its service,
 *  by design (see AdminAuditLogService/AuditService). */
@RestController
@RequestMapping("/api/admin/audit-logs")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    public AdminAuditLogController(AdminAuditLogService adminAuditLogService) {
        this.adminAuditLogService = adminAuditLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminAuditLogDto>> list(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(adminAuditLogService.listLogs(actorId, action, entityType, from, to, page, size)));
    }
}
