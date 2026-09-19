package com.nukkad.grant.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.grant.dto.CreateGrantRequest;
import com.nukkad.grant.dto.GrantDto;
import com.nukkad.grant.dto.UpdateGrantRequest;
import com.nukkad.grant.service.GrantService;
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
@RequestMapping("/api/grants")
@SecurityRequirement(name = "bearerAuth")
public class GrantController {

    private final GrantService grantService;

    public GrantController(GrantService grantService) {
        this.grantService = grantService;
    }

    @GetMapping
    public ApiResponse<PageResponse<GrantDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @RequestParam(required = false) String q,
                                                      @RequestParam(required = false) String providerType,
                                                      @RequestParam(required = false) String stage,
                                                      @RequestParam(required = false) String sector,
                                                      @RequestParam(defaultValue = "false") boolean includeExpired,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        String viewerId = principal == null ? null : principal.id();
        return ApiResponse.ok(PageResponse.from(
                grantService.listGrants(q, providerType, stage, sector, includeExpired, viewerId, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<GrantDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        String viewerId = principal == null ? null : principal.id();
        return ApiResponse.ok(grantService.getGrant(id, viewerId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GrantDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @Valid @RequestBody CreateGrantRequest request) {
        return ApiResponse.ok(grantService.createGrant(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<GrantDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @PathVariable String id,
                                         @Valid @RequestBody UpdateGrantRequest request) {
        return ApiResponse.ok(grantService.updateGrant(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        grantService.deleteGrant(principal.id(), id);
        return ApiResponse.ok(null);
    }
}
