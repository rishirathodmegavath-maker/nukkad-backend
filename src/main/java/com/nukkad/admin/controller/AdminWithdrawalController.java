package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminWithdrawalDto;
import com.nukkad.admin.dto.RejectWithdrawalRequest;
import com.nukkad.admin.service.AdminWithdrawalService;
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
@RequestMapping("/api/admin/withdrawals")
@SecurityRequirement(name = "bearerAuth")
public class AdminWithdrawalController {

    private final AdminWithdrawalService adminWithdrawalService;

    public AdminWithdrawalController(AdminWithdrawalService adminWithdrawalService) {
        this.adminWithdrawalService = adminWithdrawalService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminWithdrawalDto>> list(@RequestParam(required = false) String status,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(adminWithdrawalService.list(status, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminWithdrawalDto> get(@PathVariable String id) {
        return ApiResponse.ok(adminWithdrawalService.get(id));
    }

    @PatchMapping("/{id}/approve")
    public ApiResponse<AdminWithdrawalDto> approve(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @PathVariable String id,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminWithdrawalService.approve(principal.id(), id, httpRequest.getRemoteAddr()));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<AdminWithdrawalDto> reject(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable String id,
                                                    @Valid @RequestBody RejectWithdrawalRequest request,
                                                    HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminWithdrawalService.reject(
                principal.id(), id, request.reason(), httpRequest.getRemoteAddr()));
    }
}
