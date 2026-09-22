package com.nukkad.discussion.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.discussion.dto.CreateDiscussionRequest;
import com.nukkad.discussion.dto.DiscussionCommentDto;
import com.nukkad.discussion.dto.DiscussionDto;
import com.nukkad.discussion.dto.DiscussionStatsDto;
import com.nukkad.discussion.dto.TopicCountDto;
import com.nukkad.discussion.dto.VoteRequest;
import com.nukkad.discussion.service.DiscussionService;
import com.nukkad.feed.dto.CreateCommentRequest;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Discussions on top of the Feed's own {@code posts} table: everything a discussion needs beyond a
 * plain post (topic, votes, views, follow, reply likes). Liking, saving and uploading an attachment
 * for a discussion still go through the existing {@code /api/feed/{id}/like}, {@code /save} and
 * {@code /attachments} endpoints unchanged — no need to duplicate those here.
 */
@RestController
@RequestMapping("/api/discussions")
@SecurityRequirement(name = "bearerAuth")
public class DiscussionController {

    private final DiscussionService discussionService;

    public DiscussionController(DiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    @GetMapping
    public ApiResponse<PageResponse<DiscussionDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                           @RequestParam(defaultValue = "recent") String sort,
                                                           @RequestParam(required = false) String topic,
                                                           @RequestParam(required = false) String tag,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        String viewerId = principal == null ? null : principal.id();
        return ApiResponse.ok(PageResponse.from(discussionService.list(viewerId, sort, topic, tag, page, size)));
    }

    @GetMapping("/topics")
    public ApiResponse<List<TopicCountDto>> topics() {
        return ApiResponse.ok(discussionService.listTopics());
    }

    @GetMapping("/stats")
    public ApiResponse<DiscussionStatsDto> stats() {
        return ApiResponse.ok(discussionService.getStats());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiscussionDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @Valid @RequestBody CreateDiscussionRequest request) {
        return ApiResponse.ok(discussionService.create(principal.id(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<DiscussionDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        String viewerId = principal == null ? null : principal.id();
        DiscussionDto dto = discussionService.get(viewerId, id);
        discussionService.recordView(id, viewerId);
        return ApiResponse.ok(dto);
    }

    @PostMapping("/{id}/vote")
    public ApiResponse<DiscussionDto> vote(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                            @Valid @RequestBody VoteRequest request) {
        return ApiResponse.ok(discussionService.castVote(principal.id(), id, request.direction()));
    }

    @PostMapping("/{id}/follow")
    public ApiResponse<Map<String, Boolean>> follow(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        var result = discussionService.toggleFollow(principal.id(), id);
        return ApiResponse.ok(Map.of("following", result.following()));
    }

    @GetMapping("/{id}/comments")
    public ApiResponse<PageResponse<DiscussionCommentDto>> listComments(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                          @PathVariable String id,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "50") int size) {
        String viewerId = principal == null ? null : principal.id();
        return ApiResponse.ok(PageResponse.from(discussionService.listComments(viewerId, id, page, size)));
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiscussionCommentDto> addComment(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @PathVariable String id, @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.ok(discussionService.addComment(principal.id(), id, request));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                            @PathVariable String commentId) {
        discussionService.deleteComment(principal.id(), id, commentId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/comments/{commentId}/like")
    public ApiResponse<DiscussionCommentDto> toggleCommentLike(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @PathVariable String id, @PathVariable String commentId) {
        return ApiResponse.ok(discussionService.toggleCommentLike(principal.id(), id, commentId));
    }

    @GetMapping("/{id}/related")
    public ApiResponse<List<DiscussionDto>> related(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                                      @RequestParam(defaultValue = "5") int limit) {
        String viewerId = principal == null ? null : principal.id();
        return ApiResponse.ok(discussionService.listRelated(viewerId, id, limit));
    }

    @GetMapping("/{id}/participants")
    public ApiResponse<List<String>> participants(@PathVariable String id) {
        return ApiResponse.ok(discussionService.listParticipants(id));
    }
}
