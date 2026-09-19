package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdjustWalletBalanceRequest;
import com.nukkad.admin.dto.AdminWalletDto;
import com.nukkad.admin.dto.SetWalletStatusRequest;
import com.nukkad.admin.service.AdminWalletService;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.wallet.dto.WalletTransactionDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The acting admin is always {@code principal.id()} from the verified JWT — never a client-
 *  supplied actorId/adminId. Target users are addressed only by the {userId} path variable, and
 *  {@code /api/admin/**} is already restricted to ROLE_ADMIN by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/wallets")
@SecurityRequirement(name = "bearerAuth")
public class AdminWalletController {

    private final AdminWalletService adminWalletService;

    public AdminWalletController(AdminWalletService adminWalletService) {
        this.adminWalletService = adminWalletService;
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminWalletDto> getWallet(@PathVariable String userId) {
        return ApiResponse.ok(adminWalletService.getWallet(userId));
    }

    @GetMapping("/{userId}/transactions")
    public ApiResponse<PageResponse<WalletTransactionDto>> getTransactions(@PathVariable String userId,
                                                                             @RequestParam(defaultValue = "0") int page,
                                                                             @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(adminWalletService.listTransactions(userId, page, size)));
    }

    @PostMapping("/{userId}/adjustments")
    public ApiResponse<AdminWalletDto> adjustBalance(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @PathVariable String userId,
                                                       @Valid @RequestBody AdjustWalletBalanceRequest request,
                                                       HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminWalletService.adjustBalance(
                principal.id(), userId, request, httpRequest.getRemoteAddr()));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<AdminWalletDto> setStatus(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @PathVariable String userId,
                                                   @Valid @RequestBody SetWalletStatusRequest request,
                                                   HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminWalletService.setStatus(
                principal.id(), userId, request, httpRequest.getRemoteAddr()));
    }
}
