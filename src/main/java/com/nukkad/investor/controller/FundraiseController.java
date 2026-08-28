package com.nukkad.investor.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.investor.dto.CreateFundraiseRequest;
import com.nukkad.investor.dto.FundraiseDto;
import com.nukkad.investor.dto.UpdateFundraiseRequest;
import com.nukkad.investor.service.FundraiseService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/fundraises")
@SecurityRequirement(name = "bearerAuth")
public class FundraiseController {

    private final FundraiseService fundraiseService;

    public FundraiseController(FundraiseService fundraiseService) {
        this.fundraiseService = fundraiseService;
    }

    @GetMapping
    public ApiResponse<PageResponse<FundraiseDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String stage,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(fundraiseService.list(status, stage, principal.id(), page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<FundraiseDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(fundraiseService.get(id, principal.id()));
    }

    @GetMapping("/by-startup/{startupId}")
    public ApiResponse<FundraiseDto> byStartup(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String startupId) {
        return ApiResponse.ok(fundraiseService.getByStartup(startupId, principal.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FundraiseDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @Valid @RequestBody CreateFundraiseRequest request) {
        return ApiResponse.ok(fundraiseService.create(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FundraiseDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable String id,
                                              @RequestBody UpdateFundraiseRequest request) {
        return ApiResponse.ok(fundraiseService.update(principal.id(), id, request));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<FundraiseDto> close(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(fundraiseService.close(principal.id(), id));
    }
}
