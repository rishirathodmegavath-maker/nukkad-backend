package com.nukkad.opportunity.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.opportunity.dto.ApplicationDto;
import com.nukkad.opportunity.dto.ApplyToOpportunityRequest;
import com.nukkad.opportunity.dto.OpportunityDto;
import com.nukkad.opportunity.dto.PostOpportunityRequest;
import com.nukkad.opportunity.dto.UpdateOpportunityRequest;
import com.nukkad.opportunity.service.OpportunityService;
import com.nukkad.matching.dto.OpportunityMatchDto;
import com.nukkad.matching.service.ContentMatchingService;
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

import java.util.List;

@RestController
@RequestMapping("/api/opportunities")
@SecurityRequirement(name = "bearerAuth")
public class OpportunityController {

    private final OpportunityService opportunityService;
    private final ContentMatchingService contentMatchingService;

    public OpportunityController(OpportunityService opportunityService, ContentMatchingService contentMatchingService) {
        this.opportunityService = opportunityService;
        this.contentMatchingService = contentMatchingService;
    }

    /** TF-IDF/cosine ranking of open opportunities against the viewer's profile — see ContentMatchingService. */
    @GetMapping("/recommended")
    public ApiResponse<List<OpportunityMatchDto>> recommended(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(contentMatchingService.rankOpportunitiesForUser(principal.id(), limit));
    }

    @GetMapping("/{id}/match")
    public ApiResponse<OpportunityMatchDto> match(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(contentMatchingService.singleOpportunityMatch(principal.id(), id));
    }

    @GetMapping
    public ApiResponse<PageResponse<OpportunityDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @RequestParam(required = false) String q,
                                                            @RequestParam(required = false) String type,
                                                            @RequestParam(required = false) Boolean remote,
                                                            @RequestParam(required = false) String chapterId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                opportunityService.listOpportunities(q, type, remote, chapterId, principal.id(), page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<OpportunityDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(opportunityService.getOpportunity(id, principal.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OpportunityDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @Valid @RequestBody PostOpportunityRequest request) {
        return ApiResponse.ok(opportunityService.postOpportunity(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OpportunityDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable String id,
                                               @Valid @RequestBody UpdateOpportunityRequest request) {
        return ApiResponse.ok(opportunityService.updateOpportunity(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        opportunityService.deleteOpportunity(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/interest")
    public ApiResponse<Void> expressInterest(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        opportunityService.expressInterest(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/apply")
    public ApiResponse<ApplicationDto> apply(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                              @Valid @RequestBody ApplyToOpportunityRequest request) {
        return ApiResponse.ok(opportunityService.apply(principal.id(), id, request));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        opportunityService.withdrawApplication(principal.id(), id);
        return ApiResponse.ok(null);
    }

    /** Owner-only — the full applicant list for this opportunity, richest-first (see ApplicationDto). */
    @GetMapping("/{id}/applications")
    public ApiResponse<PageResponse<ApplicationDto>> applications(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                    @PathVariable String id,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(opportunityService.listApplications(principal.id(), id, status, page, size)));
    }

    @GetMapping("/applications/{applicationId}")
    public ApiResponse<ApplicationDto> getApplication(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @PathVariable String applicationId) {
        return ApiResponse.ok(opportunityService.getApplication(principal.id(), applicationId));
    }

    @PostMapping("/applications/{applicationId}/shortlist")
    public ApiResponse<ApplicationDto> shortlist(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String applicationId) {
        return ApiResponse.ok(opportunityService.shortlistApplication(principal.id(), applicationId));
    }

    @PostMapping("/applications/{applicationId}/accept")
    public ApiResponse<ApplicationDto> accept(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String applicationId) {
        return ApiResponse.ok(opportunityService.acceptApplication(principal.id(), applicationId));
    }

    @PostMapping("/applications/{applicationId}/reject")
    public ApiResponse<ApplicationDto> reject(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String applicationId) {
        return ApiResponse.ok(opportunityService.rejectApplication(principal.id(), applicationId));
    }

    @GetMapping("/me/posted")
    public ApiResponse<PageResponse<OpportunityDto>> myPosted(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(opportunityService.listMyPosted(principal.id(), page, size)));
    }

    @GetMapping("/me/applications")
    public ApiResponse<PageResponse<OpportunityDto>> myApplications(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(opportunityService.listMyApplications(principal.id(), page, size)));
    }
}
