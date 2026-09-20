package com.nukkad.wallet.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.wallet.dto.RequestWithdrawalRequest;
import com.nukkad.wallet.dto.WalletDto;
import com.nukkad.wallet.dto.WalletTransactionDto;
import com.nukkad.wallet.dto.WithdrawalRequestDto;
import com.nukkad.wallet.entity.Wallet;
import com.nukkad.wallet.mapper.WalletMapper;
import com.nukkad.wallet.security.RequiresWalletUnlock;
import com.nukkad.wallet.service.WalletService;
import com.nukkad.wallet.service.WithdrawalService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately exposes only "my own wallet" shapes — {@code /me}, never {@code /{walletId}} — so
 * there is no walletId/userId path parameter for a client to forge in the first place. The wallet
 * always comes from {@code principal.id()}, the authenticated identity, never a client-supplied id.
 *
 * <p>{@code @RequiresWalletUnlock}: every endpoint here also needs the member's wallet PIN to have
 * been entered recently (X-Wallet-Token header, see WalletPinController). Because the annotation is
 * on the class, an endpoint added here later is protected without anyone having to remember it.
 */
@RestController
@RequestMapping("/api/wallet")
@SecurityRequirement(name = "bearerAuth")
@RequiresWalletUnlock
public class WalletController {

    private final WalletService walletService;
    private final WalletMapper walletMapper;
    private final WithdrawalService withdrawalService;

    public WalletController(WalletService walletService, WalletMapper walletMapper, WithdrawalService withdrawalService) {
        this.walletService = walletService;
        this.walletMapper = walletMapper;
        this.withdrawalService = withdrawalService;
    }

    @GetMapping("/me")
    public ApiResponse<WalletDto> getMyWallet(@AuthenticationPrincipal AuthenticatedUser principal) {
        Wallet wallet = walletService.getOrCreateWallet(principal.id());
        return ApiResponse.ok(walletMapper.toDto(wallet));
    }

    @GetMapping("/me/transactions")
    public ApiResponse<PageResponse<WalletTransactionDto>> getMyTransactions(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Wallet wallet = walletService.getOrCreateWallet(principal.id());
        return ApiResponse.ok(PageResponse.from(
                walletService.listTransactions(wallet.getId(), page, size).map(walletMapper::toDto)));
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WithdrawalRequestDto> requestWithdrawal(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @Valid @RequestBody RequestWithdrawalRequest request) {
        return ApiResponse.ok(walletMapper.toDto(
                withdrawalService.request(principal.id(), request.amountMinorUnits(), request.note())));
    }

    @GetMapping("/withdrawals")
    public ApiResponse<PageResponse<WithdrawalRequestDto>> myWithdrawals(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                           @RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                withdrawalService.listMine(principal.id(), page, size).map(walletMapper::toDto)));
    }

    @PostMapping("/withdrawals/{id}/cancel")
    public ApiResponse<WithdrawalRequestDto> cancelWithdrawal(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @PathVariable String id) {
        return ApiResponse.ok(walletMapper.toDto(withdrawalService.cancel(principal.id(), id)));
    }
}
