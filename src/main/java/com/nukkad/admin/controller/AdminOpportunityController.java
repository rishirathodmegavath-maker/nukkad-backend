package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminPostOpportunityRequest;
import com.nukkad.admin.dto.ReviewContentRequest;
import com.nukkad.admin.dto.SetContentRemovedRequest;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.opportunity.dto.OpportunityDto;
import com.nukkad.opportunity.dto.PostOpportunityRequest;
import com.nukkad.opportunity.service.OpportunityService;
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

/** Read-only oversight plus one moderation action, reusing OpportunityService exactly as the
 *  public /api/opportunities endpoints do. By default the admin list matches public discovery
 *  (closed/removed postings excluded); includeClosed/includeRemoved opt into seeing them too. Also lets
 *  an admin post an opportunity directly (see OpportunityService#postOpportunityAsAdmin). */
@RestController
@RequestMapping("/api/admin/opportunities")
@SecurityRequirement(name = "bearerAuth")
public class AdminOpportunityController {

    private final OpportunityService opportunityService;

    public AdminOpportunityController(OpportunityService opportunityService) {
        this.opportunityService = opportunityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OpportunityDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @Valid @RequestBody AdminPostOpportunityRequest request,
                                               HttpServletRequest httpRequest) {
        PostOpportunityRequest opportunity = new PostOpportunityRequest(request.title(), request.type(), null,
                request.organizationName(), request.location(), request.workMode(), request.description(),
                request.responsibilities(), request.requirements(), request.requiredSkills(), request.compensation(),
                request.equity(), request.experienceLevel(), request.applicationDeadline());
        return ApiResponse.ok(opportunityService.postOpportunityAsAdmin(
                principal.id(), opportunity, request.postedByEmail(), request.publisherIdentity(), httpRequest.getRemoteAddr()));
    }

    @GetMapping
    public ApiResponse<PageResponse<OpportunityDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @RequestParam(required = false) String q,
                                                            @RequestParam(required = false) String type,
                                                            @RequestParam(required = false) String chapterId,
                                                            @RequestParam(required = false) String postedByUserId,
                                                            @RequestParam(defaultValue = "false") boolean includeClosed,
                                                            @RequestParam(defaultValue = "false") boolean includeRemoved,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        var result = opportunityService.listOpportunitiesForAdmin(q, type, null, chapterId, null, postedByUserId,
                principal.id(), includeClosed, includeRemoved, parseModerationStatus(status), page, AdminPaging.clampSize(size));
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<OpportunityDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(opportunityService.getOpportunityForAdmin(id, principal.id()));
    }

    @PatchMapping("/{id}/removed")
    public ApiResponse<OpportunityDto> setRemoved(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable String id,
                                                    @Valid @RequestBody SetContentRemovedRequest request,
                                                    HttpServletRequest httpRequest) {
        return ApiResponse.ok(opportunityService.setRemovedByAdmin(
                principal.id(), id, request.removed(), request.reason(), httpRequest.getRemoteAddr()));
    }

    @PatchMapping("/{id}/moderation")
    public ApiResponse<OpportunityDto> review(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable String id,
                                               @Valid @RequestBody ReviewContentRequest request,
                                               HttpServletRequest httpRequest) {
        return ApiResponse.ok(opportunityService.reviewModeration(
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
