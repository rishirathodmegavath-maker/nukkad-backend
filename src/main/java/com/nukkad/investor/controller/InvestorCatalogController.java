package com.nukkad.investor.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.investor.dto.InvestorDto;
import com.nukkad.investor.dto.InvestorIntroductionResultDto;
import com.nukkad.investor.dto.RequestInvestorIntroductionRequest;
import com.nukkad.investor.service.InvestorCatalogService;
import com.nukkad.security.AuthenticatedUser;
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
 * Investor Discovery — founders searching the admin-managed investor catalog. Every method requires an active
 * Startup Profile; {@link InvestorCatalogService} enforces this itself (not just here), so a direct API call gets
 * the same 403 a browser would. See {@code AdminInvestorCatalogController} for how the catalog is managed.
 */
@RestController
@RequestMapping("/api/investor-catalog")
@SecurityRequirement(name = "bearerAuth")
public class InvestorCatalogController {

    private final InvestorCatalogService investorCatalogService;

    public InvestorCatalogController(InvestorCatalogService investorCatalogService) {
        this.investorCatalogService = investorCatalogService;
    }

    /** Whether the caller may use Investor Discovery at all — the frontend checks this first to decide between
     *  the locked state and the real page, without needing to attempt (and fail) a real list call. */
    @GetMapping("/access")
    public ApiResponse<Boolean> access(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(investorCatalogService.hasAccess(principal.id()));
    }

    @GetMapping
    public ApiResponse<PageResponse<InvestorDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(required = false) String type,
                                                          @RequestParam(required = false) String sector,
                                                          @RequestParam(required = false) String stage,
                                                          @RequestParam(required = false) String location,
                                                          @RequestParam(required = false) String country,
                                                          @RequestParam(required = false) Long chequeSize,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                investorCatalogService.list(type, sector, stage, location, country, chequeSize, q, principal.id(), page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<InvestorDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(investorCatalogService.get(id, principal.id()));
    }

    @PostMapping("/{id}/introductions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvestorIntroductionResultDto> requestIntroduction(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                            @PathVariable String id,
                                                                            @Valid @RequestBody RequestInvestorIntroductionRequest request) {
        return ApiResponse.ok(investorCatalogService.requestIntroduction(principal.id(), id, request));
    }
}
