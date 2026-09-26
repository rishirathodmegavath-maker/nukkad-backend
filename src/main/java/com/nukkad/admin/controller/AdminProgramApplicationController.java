package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminChangeProgramApplicationStatusRequest;
import com.nukkad.admin.dto.AdminProgramApplicationDto;
import com.nukkad.admin.service.AdminProgramApplicationService;
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

/** Gated automatically by SecurityConfig's existing {@code /api/admin/**} matcher
 *  (ROLE_ADMIN + SCOPE_ADMIN) — no new security rule needed. */
@RestController
@RequestMapping("/api/admin/program-applications")
@SecurityRequirement(name = "bearerAuth")
public class AdminProgramApplicationController {

    private final AdminProgramApplicationService adminProgramApplicationService;

    public AdminProgramApplicationController(AdminProgramApplicationService adminProgramApplicationService) {
        this.adminProgramApplicationService = adminProgramApplicationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminProgramApplicationDto>> list(@RequestParam(required = false) String program,
                                                                         @RequestParam(required = false) String status,
                                                                         @RequestParam(required = false) String q,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(adminProgramApplicationService.list(program, status, q, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminProgramApplicationDto> get(@PathVariable String id) {
        return ApiResponse.ok(adminProgramApplicationService.get(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminProgramApplicationDto> changeStatus(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                   @PathVariable String id,
                                                                   @Valid @RequestBody AdminChangeProgramApplicationStatusRequest request,
                                                                   HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminProgramApplicationService.changeStatus(principal.id(), id, request, httpRequest.getRemoteAddr()));
    }
}
