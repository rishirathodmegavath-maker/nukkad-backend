package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminCreateStartupRequest;
import com.nukkad.admin.dto.SetContentRemovedRequest;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.startup.dto.CreateStartupRequest;
import com.nukkad.startup.dto.StartupDto;
import com.nukkad.startup.service.StartupService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Reuses StartupService exactly as the public /api/startups endpoints do. The admin is passed as
 *  an authenticated viewer, so the existing visibility rule already grants them the same PUBLIC +
 *  NUKKAD_MEMBERS visibility every signed-in user already has. Adds three actions: an admin can add
 *  a startup (with every field a member can set when registering their own, plus a logo), and can
 *  hide a startup from public discovery (and its public detail page) without deleting it, or
 *  reverse that. */
@RestController
@RequestMapping("/api/admin/startups")
@SecurityRequirement(name = "bearerAuth")
public class AdminStartupController {

    private final StartupService startupService;

    public AdminStartupController(StartupService startupService) {
        this.startupService = startupService;
    }

    @GetMapping
    public ApiResponse<PageResponse<StartupDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(required = false) String sector,
                                                        @RequestParam(required = false) String stage,
                                                        @RequestParam(required = false) String chapterId,
                                                        @RequestParam(defaultValue = "false") boolean includeRemoved,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        var result = startupService.listStartupsForAdmin(q, sector, stage, null, chapterId, null, principal.id(),
                includeRemoved, parseModerationStatus(status), page, AdminPaging.clampSize(size));
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<StartupDto> get(@PathVariable String id) {
        return ApiResponse.ok(startupService.getStartupForAdmin(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StartupDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody AdminCreateStartupRequest request,
                                           HttpServletRequest httpRequest) {
        CreateStartupRequest startup = new CreateStartupRequest(request.name(), null, request.tagline(), request.sector(),
                request.problem(), request.solution(), request.stage(), request.needs(), blankToNull(request.chapterId()),
                request.location(), request.website(), request.targetCustomer(), request.businessModel(), request.whatBuilding(),
                request.revenue(), request.customers(), request.users(), request.growth(), request.otherTraction(),
                request.visibility(), request.fundraisingVisible());
        return ApiResponse.ok(startupService.createStartupAsAdmin(
                principal.id(), startup, request.founderEmail(), request.publisherIdentity(), httpRequest.getRemoteAddr()));
    }

    /** The logo goes through its own call once the startup exists — the same two-step shape (create,
     *  then upload) the member create-startup flow already uses. Unlike the member logo endpoint it
     *  otherwise mirrors, this one is never blocked by "must be a manager of this startup," since the
     *  founder here might not even be the admin's own account. */
    @PostMapping("/{id}/logo")
    public ApiResponse<StartupDto> uploadLogo(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(startupService.updateLogoAsAdmin(id, file));
    }

    @PatchMapping("/{id}/removed")
    public ApiResponse<StartupDto> setRemoved(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable String id,
                                               @Valid @RequestBody SetContentRemovedRequest request,
                                               HttpServletRequest httpRequest) {
        return ApiResponse.ok(startupService.setRemovedByAdmin(
                principal.id(), id, request.removed(), request.reason(), httpRequest.getRemoteAddr()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
