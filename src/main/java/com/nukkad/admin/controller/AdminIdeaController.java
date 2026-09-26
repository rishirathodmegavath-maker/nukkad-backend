package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminCreateIdeaRequest;
import com.nukkad.admin.dto.ReviewContentRequest;
import com.nukkad.admin.dto.SetContentRemovedRequest;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.idea.dto.IdeaDto;
import com.nukkad.idea.dto.PostIdeaRequest;
import com.nukkad.idea.service.IdeaService;
import com.nukkad.security.AuthenticatedUser;
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

/** Reuses IdeaService exactly as the public /api/ideas endpoints do — no Ideas business logic is
 *  duplicated here. Adds one moderation action: an admin can hide an idea from public discovery
 *  (and its public detail page) without deleting it, or reverse that. Also lets an admin post an
 *  idea directly (see IdeaService#createIdeaAsAdmin). */
@RestController
@RequestMapping("/api/admin/ideas")
@SecurityRequirement(name = "bearerAuth")
public class AdminIdeaController {

    private final IdeaService ideaService;

    public AdminIdeaController(IdeaService ideaService) {
        this.ideaService = ideaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IdeaDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @Valid @RequestBody AdminCreateIdeaRequest request,
                                        HttpServletRequest httpRequest) {
        PostIdeaRequest idea = new PostIdeaRequest(request.title(), request.problem(), request.solution(),
                request.targetCustomer(), request.stage(), request.category(), request.tags(), request.helpNeeded());
        return ApiResponse.ok(ideaService.createIdeaAsAdmin(
                principal.id(), idea, request.creatorEmail(), request.publisherIdentity(), httpRequest.getRemoteAddr()));
    }

    @GetMapping
    public ApiResponse<PageResponse<IdeaDto>> list(@RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String stage,
                                                     @RequestParam(required = false) String category,
                                                     @RequestParam(required = false) String chapterId,
                                                     @RequestParam(required = false) String creatorId,
                                                     @RequestParam(defaultValue = "false") boolean includeRemoved,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        var result = ideaService.listIdeasForAdmin(q, stage, category, null, chapterId, creatorId, includeRemoved,
                parseModerationStatus(status), page, AdminPaging.clampSize(size));
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<IdeaDto> get(@PathVariable String id) {
        return ApiResponse.ok(ideaService.getIdeaForAdmin(id));
    }

    @PatchMapping("/{id}/removed")
    public ApiResponse<IdeaDto> setRemoved(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable String id,
                                            @Valid @RequestBody SetContentRemovedRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(ideaService.setRemovedByAdmin(
                principal.id(), id, request.removed(), request.reason(), httpRequest.getRemoteAddr()));
    }

    @PatchMapping("/{id}/moderation")
    public ApiResponse<IdeaDto> review(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable String id,
                                        @Valid @RequestBody ReviewContentRequest request,
                                        HttpServletRequest httpRequest) {
        return ApiResponse.ok(ideaService.reviewModeration(
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
