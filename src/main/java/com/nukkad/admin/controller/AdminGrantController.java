package com.nukkad.admin.controller;

import com.nukkad.admin.dto.ReviewContentRequest;
import com.nukkad.admin.dto.SetContentRemovedRequest;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.grant.dto.GrantDto;
import com.nukkad.grant.service.GrantService;
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

/** Mirrors AdminIdeaController exactly — reuses GrantService with no Grants business logic
 *  duplicated here. */
@RestController
@RequestMapping("/api/admin/grants")
@SecurityRequirement(name = "bearerAuth")
public class AdminGrantController {

    private final GrantService grantService;

    public AdminGrantController(GrantService grantService) {
        this.grantService = grantService;
    }

    @GetMapping
    public ApiResponse<PageResponse<GrantDto>> list(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String providerType,
                                                       @RequestParam(required = false) String stage,
                                                       @RequestParam(required = false) String sector,
                                                       @RequestParam(defaultValue = "true") boolean includeExpired,
                                                       @RequestParam(defaultValue = "false") boolean includeRemoved,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        var result = grantService.listGrantsForAdmin(q, providerType, stage, sector, includeExpired, includeRemoved,
                parseModerationStatus(status), page, AdminPaging.clampSize(size));
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<GrantDto> get(@PathVariable String id) {
        return ApiResponse.ok(grantService.getGrantForAdmin(id));
    }

    @PatchMapping("/{id}/removed")
    public ApiResponse<GrantDto> setRemoved(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @PathVariable String id,
                                             @Valid @RequestBody SetContentRemovedRequest request,
                                             HttpServletRequest httpRequest) {
        return ApiResponse.ok(grantService.setRemovedByAdmin(
                principal.id(), id, request.removed(), request.reason(), httpRequest.getRemoteAddr()));
    }

    @PatchMapping("/{id}/moderation")
    public ApiResponse<GrantDto> review(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @PathVariable String id,
                                         @Valid @RequestBody ReviewContentRequest request,
                                         HttpServletRequest httpRequest) {
        return ApiResponse.ok(grantService.reviewModeration(
                principal.id(), id, request.approved(), request.reason(), httpRequest.getRemoteAddr()));
    }

    private ModerationStatus parseModerationStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return ModerationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
