package com.nukkad.dashboard.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.dashboard.dto.FounderDashboardDto;
import com.nukkad.dashboard.service.FounderDashboardService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/founder")
@SecurityRequirement(name = "bearerAuth")
public class FounderDashboardController {

    private final FounderDashboardService dashboardService;

    public FounderDashboardController(FounderDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ApiResponse<FounderDashboardDto> get(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(dashboardService.getDashboard(principal.id()));
    }
}
