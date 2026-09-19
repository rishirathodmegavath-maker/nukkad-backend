package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminActivityDto;
import com.nukkad.admin.service.AdminActivityService;
import com.nukkad.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/activity")
@SecurityRequirement(name = "bearerAuth")
public class AdminActivityController {

    private final AdminActivityService activityService;

    public AdminActivityController(AdminActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ApiResponse<List<AdminActivityDto>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(activityService.recent(limit));
    }
}
