package com.nukkad.wallet.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.wallet.dto.ChangeWalletPinRequest;
import com.nukkad.wallet.dto.ResetWalletPinRequest;
import com.nukkad.wallet.dto.SetWalletPinRequest;
import com.nukkad.wallet.dto.VerifyWalletPinRequest;
import com.nukkad.wallet.dto.WalletPinStatusDto;
import com.nukkad.wallet.dto.WalletUnlockDto;
import com.nukkad.wallet.service.WalletPinService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The PIN endpoints are the only wallet routes that work while the wallet is locked — by design they
 * are NOT annotated with {@code @RequiresWalletUnlock}, since they are how it gets unlocked. Every
 * one still needs a signed-in member, and the ones that create or replace a PIN also demand the
 * account password (or the old PIN).
 */
@RestController
@RequestMapping("/api/wallet/pin")
@SecurityRequirement(name = "bearerAuth")
public class WalletPinController {

    private final WalletPinService walletPinService;

    public WalletPinController(WalletPinService walletPinService) {
        this.walletPinService = walletPinService;
    }

    @GetMapping("/status")
    public ApiResponse<WalletPinStatusDto> status(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(walletPinService.status(principal.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WalletUnlockDto> set(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @Valid @RequestBody SetWalletPinRequest request,
                                            HttpServletRequest http) {
        return ApiResponse.ok(walletPinService.setPin(
                principal.id(), principal.tokenVersion(), request.pin(), request.password(), http.getRemoteAddr()));
    }

    @PostMapping("/verify")
    public ApiResponse<WalletUnlockDto> verify(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @Valid @RequestBody VerifyWalletPinRequest request,
                                               HttpServletRequest http) {
        return ApiResponse.ok(walletPinService.verify(
                principal.id(), principal.tokenVersion(), request.pin(), http.getRemoteAddr()));
    }

    @PostMapping("/change")
    public ApiResponse<WalletUnlockDto> change(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @Valid @RequestBody ChangeWalletPinRequest request,
                                               HttpServletRequest http) {
        return ApiResponse.ok(walletPinService.changePin(
                principal.id(), principal.tokenVersion(), request.currentPin(), request.newPin(), http.getRemoteAddr()));
    }

    @PostMapping("/reset")
    public ApiResponse<WalletUnlockDto> reset(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @Valid @RequestBody ResetWalletPinRequest request,
                                              HttpServletRequest http) {
        return ApiResponse.ok(walletPinService.resetPin(
                principal.id(), principal.tokenVersion(), request.password(), request.newPin(), http.getRemoteAddr()));
    }
}
