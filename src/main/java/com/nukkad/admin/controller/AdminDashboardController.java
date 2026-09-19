package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminDashboardDto;
import com.nukkad.admin.service.AdminDashboardService;
import com.nukkad.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Every endpoint under /api/admin/** requires ROLE_ADMIN — enforced centrally in
 *  SecurityConfig, not per-controller, so no admin endpoint can be added here or elsewhere
 *  without that protection. */
@RestController
@RequestMapping("/api/admin/dashboard")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping
    public ApiResponse<AdminDashboardDto> get() {
        return ApiResponse.ok(adminDashboardService.getDashboard());
    }
}
