package com.nukkad.idea.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.idea.dto.ConvertToStartupRequest;
import com.nukkad.idea.dto.ExpressInterestRequest;
import com.nukkad.idea.dto.IdeaDto;
import com.nukkad.idea.dto.IdeaInterestDto;
import com.nukkad.idea.dto.PostIdeaRequest;
import com.nukkad.idea.dto.UpdateIdeaRequest;
import com.nukkad.idea.service.IdeaService;
import com.nukkad.matching.dto.IdeaMatchDto;
import com.nukkad.matching.service.ContentMatchingService;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.startup.dto.StartupDto;
import com.nukkad.user.dto.UserDto;
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
@RequestMapping("/api/ideas")
@SecurityRequirement(name = "bearerAuth")
public class IdeaController {

    private final IdeaService ideaService;
    private final ContentMatchingService contentMatchingService;

    public IdeaController(IdeaService ideaService, ContentMatchingService contentMatchingService) {
        this.ideaService = ideaService;
        this.contentMatchingService = contentMatchingService;
    }

    /** TF-IDF/cosine ranking of open ideas against the viewer's profile — see ContentMatchingService. */
    @GetMapping("/recommended")
    public ApiResponse<List<IdeaMatchDto>> recommended(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(contentMatchingService.rankIdeasForUser(principal.id(), limit));
    }

    @GetMapping("/{id}/match")
    public ApiResponse<IdeaMatchDto> match(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(contentMatchingService.singleIdeaMatch(principal.id(), id));
    }

    @GetMapping
    public ApiResponse<PageResponse<IdeaDto>> list(@RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String stage,
                                                     @RequestParam(required = false) String category,
                                                     @RequestParam(required = false) String helpNeeded,
                                                     @RequestParam(required = false) String chapterId,
                                                     @RequestParam(required = false) String creatorId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(ideaService.listIdeas(q, stage, category, helpNeeded, chapterId, creatorId, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<IdeaDto> get(@PathVariable String id) {
        return ApiResponse.ok(ideaService.getIdea(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IdeaDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @Valid @RequestBody PostIdeaRequest request) {
        return ApiResponse.ok(ideaService.postIdea(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<IdeaDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable String id,
                                        @Valid @RequestBody UpdateIdeaRequest request) {
        return ApiResponse.ok(ideaService.updateIdea(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        ideaService.deleteIdea(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/interest")
    public ApiResponse<IdeaInterestDto> expressInterest(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @PathVariable String id,
                                                          @Valid @RequestBody ExpressInterestRequest request) {
        return ApiResponse.ok(ideaService.expressInterest(principal.id(), id, request));
    }

    @PostMapping("/{id}/interest/withdraw")
    public ApiResponse<Void> withdrawInterest(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        ideaService.withdrawInterest(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/interests/{interestId}/shortlist")
    public ApiResponse<IdeaInterestDto> shortlistInterest(@AuthenticationPrincipal AuthenticatedUser principal,
                                                           @PathVariable String interestId) {
        return ApiResponse.ok(ideaService.shortlistInterest(principal.id(), interestId));
    }

    @PostMapping("/interests/{interestId}/reject")
    public ApiResponse<IdeaInterestDto> rejectInterest(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @PathVariable String interestId) {
        return ApiResponse.ok(ideaService.rejectInterest(principal.id(), interestId));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<MembersResponse> members(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        var result = ideaService.getMembers(principal.id(), id);
        return ApiResponse.ok(new MembersResponse(result.team(), result.interests()));
    }

    public record MembersResponse(List<UserDto> team, List<IdeaInterestDto> interests) {}

    @PostMapping("/{id}/team/{userId}")
    public ApiResponse<IdeaDto> addToTeam(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @PathVariable String id, @PathVariable String userId) {
        return ApiResponse.ok(ideaService.addToTeam(principal.id(), id, userId));
    }

    @DeleteMapping("/{id}/team/{userId}")
    public ApiResponse<IdeaDto> removeFromTeam(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable String id, @PathVariable String userId) {
        return ApiResponse.ok(ideaService.removeFromTeam(principal.id(), id, userId));
    }

    @PostMapping("/{id}/convert-to-startup")
    public ApiResponse<StartupDto> convertToStartup(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable String id,
                                                      @RequestBody(required = false) ConvertToStartupRequest request) {
        var payload = request == null ? new ConvertToStartupRequest(null, null, null) : request;
        return ApiResponse.ok(ideaService.convertToStartup(principal.id(), id, payload));
    }
}
