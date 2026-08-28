package com.nukkad.investor.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.investor.dto.CreateInvestorProfileRequest;
import com.nukkad.investor.dto.InvestorProfileDto;
import com.nukkad.investor.dto.UpdateInvestorProfileRequest;
import com.nukkad.investor.service.InvestorProfileService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investors")
@SecurityRequirement(name = "bearerAuth")
public class InvestorProfileController {

    private final InvestorProfileService investorProfileService;

    public InvestorProfileController(InvestorProfileService investorProfileService) {
        this.investorProfileService = investorProfileService;
    }

    @GetMapping
    public ApiResponse<PageResponse<InvestorProfileDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @RequestParam(required = false) String q,
                                                                 @RequestParam(required = false) String type,
                                                                 @RequestParam(required = false) String sector,
                                                                 @RequestParam(required = false) String stage,
                                                                 @RequestParam(required = false) String geography,
                                                                 @RequestParam(required = false) Long ticketSize,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                investorProfileService.list(type, sector, stage, geography, ticketSize, q, principal.id(), page, size)));
    }

    @GetMapping("/me")
    public ApiResponse<InvestorProfileDto> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(investorProfileService.getMine(principal.id()));
    }

    @GetMapping("/by-user/{userId}")
    public ApiResponse<InvestorProfileDto> byUser(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String userId) {
        return ApiResponse.ok(investorProfileService.getByUserId(userId, principal.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<InvestorProfileDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(investorProfileService.get(id, principal.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvestorProfileDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody CreateInvestorProfileRequest request) {
        return ApiResponse.ok(investorProfileService.create(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<InvestorProfileDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable String id,
                                                    @Valid @RequestBody UpdateInvestorProfileRequest request) {
        return ApiResponse.ok(investorProfileService.update(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        investorProfileService.delete(principal.id(), id);
        return ApiResponse.ok(null);
    }
}
