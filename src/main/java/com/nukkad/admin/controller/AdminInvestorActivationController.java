package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminInvestorActivationDto;
import com.nukkad.admin.dto.RejectInvestorActivationRequest;
import com.nukkad.admin.service.AdminInvestorActivationService;
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
@RequestMapping("/api/admin/investor-activation-requests")
@SecurityRequirement(name = "bearerAuth")
public class AdminInvestorActivationController {

    private final AdminInvestorActivationService adminInvestorActivationService;

    public AdminInvestorActivationController(AdminInvestorActivationService adminInvestorActivationService) {
        this.adminInvestorActivationService = adminInvestorActivationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminInvestorActivationDto>> list(@RequestParam(required = false) String status,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(adminInvestorActivationService.list(status, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminInvestorActivationDto> get(@PathVariable String id) {
        return ApiResponse.ok(adminInvestorActivationService.get(id));
    }

    @PatchMapping("/{id}/approve")
    public ApiResponse<AdminInvestorActivationDto> approve(@AuthenticationPrincipal AuthenticatedUser principal,
                                                              @PathVariable String id,
                                                              HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminInvestorActivationService.approve(principal.id(), id, httpRequest.getRemoteAddr()));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<AdminInvestorActivationDto> reject(@AuthenticationPrincipal AuthenticatedUser principal,
                                                             @PathVariable String id,
                                                             @Valid @RequestBody RejectInvestorActivationRequest request,
                                                             HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminInvestorActivationService.reject(
                principal.id(), id, request.reason(), httpRequest.getRemoteAddr()));
    }
}
