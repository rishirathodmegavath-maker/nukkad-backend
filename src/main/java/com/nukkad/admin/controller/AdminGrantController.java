package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminCreateGrantRequest;
import com.nukkad.admin.dto.ReviewContentRequest;
import com.nukkad.admin.dto.SetContentRemovedRequest;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.grant.dto.CreateGrantRequest;
import com.nukkad.grant.dto.GrantDto;
import com.nukkad.grant.dto.GrantImportBatchDto;
import com.nukkad.grant.dto.GrantImportIssueDto;
import com.nukkad.grant.dto.GrantImportPreviewDto;
import com.nukkad.grant.service.GrantImportService;
import com.nukkad.grant.service.GrantService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

/** Mirrors AdminIdeaController exactly — reuses GrantService with no Grants business logic
 *  duplicated here. Also lets an admin publish a grant listing directly (see
 *  GrantService#createGrantAsAdmin), or bulk-import many at once from a spreadsheet (see
 *  GrantImportService) — the manual, zero-API-cost replacement for the disabled Gemini discovery
 *  pipeline. */
@RestController
@RequestMapping("/api/admin/grants")
@SecurityRequirement(name = "bearerAuth")
public class AdminGrantController {

    private final GrantService grantService;
    private final GrantImportService grantImportService;

    public AdminGrantController(GrantService grantService, GrantImportService grantImportService) {
        this.grantService = grantService;
        this.grantImportService = grantImportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GrantDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @Valid @RequestBody AdminCreateGrantRequest request,
                                         HttpServletRequest httpRequest) {
        CreateGrantRequest grant = new CreateGrantRequest(request.name(), request.provider(), request.providerType(),
                request.description(), request.fundingAmount(), request.eligibilityCriteria(), request.eligibleSectors(),
                request.eligibleStages(), request.deadline(), request.applicationUrl());
        return ApiResponse.ok(grantService.createGrantAsAdmin(
                principal.id(), grant, request.createdByEmail(), request.publisherIdentity(), httpRequest.getRemoteAddr()));
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

    // ---- Bulk spreadsheet import — see GrantImportService ----

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<GrantImportPreviewDto> previewImport(@RequestParam MultipartFile file) {
        return ApiResponse.ok(grantImportService.preview(file));
    }

    /** Re-parses the same file (the browser re-sends it, having already previewed it) and starts an
     *  async import; the admin UI polls GET .../import/{id} for progress and the final report. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<GrantImportBatchDto> startImport(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @RequestParam MultipartFile file) {
        return ApiResponse.ok(grantImportService.startImport(principal.id(), file));
    }

    @GetMapping("/import")
    public ApiResponse<PageResponse<GrantImportBatchDto>> listImports(@RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(grantImportService.listBatches(page, AdminPaging.clampSize(size))));
    }

    @GetMapping("/import/{id}")
    public ApiResponse<GrantImportBatchDto> getImport(@PathVariable String id) {
        return ApiResponse.ok(grantImportService.getBatch(id));
    }

    @GetMapping("/import/{id}/issues")
    public ApiResponse<PageResponse<GrantImportIssueDto>> listImportIssues(@PathVariable String id,
                                                                              @RequestParam(defaultValue = "0") int page,
                                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(grantImportService.listIssues(id, page, AdminPaging.clampSize(size))));
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
