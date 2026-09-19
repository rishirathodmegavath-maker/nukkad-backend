package com.nukkad.investor.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.investor.dto.InvestorActivationRequestDto;
import com.nukkad.investor.dto.SubmitInvestorActivationRequest;
import com.nukkad.investor.service.InvestorActivationService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investors/activation-requests")
@SecurityRequirement(name = "bearerAuth")
public class InvestorActivationController {

    private final InvestorActivationService investorActivationService;

    public InvestorActivationController(InvestorActivationService investorActivationService) {
        this.investorActivationService = investorActivationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvestorActivationRequestDto> submit(@AuthenticationPrincipal AuthenticatedUser principal,
                                                               @Valid @RequestBody SubmitInvestorActivationRequest request) {
        return ApiResponse.ok(investorActivationService.submit(principal.id(), request));
    }

    @GetMapping("/me")
    public ApiResponse<InvestorActivationRequestDto> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(investorActivationService.getMine(principal.id()));
    }
}
