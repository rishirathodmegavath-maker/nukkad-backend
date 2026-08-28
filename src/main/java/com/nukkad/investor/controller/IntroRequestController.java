package com.nukkad.investor.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.investor.dto.CreateIntroRequestRequest;
import com.nukkad.investor.dto.IntroRequestDto;
import com.nukkad.investor.service.IntroRequestService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/intro-requests")
@SecurityRequirement(name = "bearerAuth")
public class IntroRequestController {

    private final IntroRequestService introRequestService;

    public IntroRequestController(IntroRequestService introRequestService) {
        this.introRequestService = introRequestService;
    }

    @GetMapping("/inbox")
    public ApiResponse<List<IntroRequestDto>> inbox(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(introRequestService.inbox(principal.id()));
    }

    @GetMapping("/sent")
    public ApiResponse<List<IntroRequestDto>> sent(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(introRequestService.sent(principal.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<IntroRequestDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(introRequestService.get(id, principal.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IntroRequestDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @Valid @RequestBody CreateIntroRequestRequest request) {
        return ApiResponse.ok(introRequestService.create(principal.id(), request));
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<IntroRequestDto> accept(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(introRequestService.accept(principal.id(), id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<IntroRequestDto> reject(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(introRequestService.reject(principal.id(), id));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<IntroRequestDto> withdraw(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(introRequestService.withdraw(principal.id(), id));
    }
}
