package com.nukkad.feed.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.feed.dto.AttachmentRef;
import com.nukkad.feed.dto.CommentDto;
import com.nukkad.feed.dto.CreateCommentRequest;
import com.nukkad.feed.dto.CreatePostRequest;
import com.nukkad.feed.dto.PostDto;
import com.nukkad.feed.dto.UpdatePostRequest;
import com.nukkad.feed.service.FeedService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/feed")
@SecurityRequirement(name = "bearerAuth")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PostDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @RequestParam(required = false) String authorId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(feedService.list(principal.id(), authorId, page, size)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostDto> create(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.ok(feedService.create(principal.id(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PostDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(feedService.get(principal.id(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        feedService.delete(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}")
    public ApiResponse<PostDto> update(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                        @Valid @RequestBody UpdatePostRequest request) {
        return ApiResponse.ok(feedService.update(principal.id(), id, request));
    }

    @PatchMapping("/{id}/hide-like-count")
    public ApiResponse<PostDto> toggleHideLikeCount(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(feedService.toggleHideLikeCount(principal.id(), id));
    }

    @PatchMapping("/{id}/comments-disabled")
    public ApiResponse<PostDto> toggleCommentsDisabled(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(feedService.toggleCommentsDisabled(principal.id(), id));
    }

    @PostMapping("/{id}/like")
    public ApiResponse<PostDto> toggleLike(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        try {
            return ApiResponse.ok(feedService.toggleLike(principal.id(), id));
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous like-toggle requests both saw "not liked yet" and raced to insert;
            // the loser's transaction has already rolled back cleanly by the time it reaches here.
            // The desired end state (liked) is true regardless of which request "won" — return that.
            return ApiResponse.ok(feedService.get(principal.id(), id));
        }
    }

    @PostMapping("/{id}/save")
    public ApiResponse<PostDto> toggleSave(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        try {
            return ApiResponse.ok(feedService.toggleSave(principal.id(), id));
        } catch (DataIntegrityViolationException e) {
            return ApiResponse.ok(feedService.get(principal.id(), id));
        }
    }

    @GetMapping("/{id}/comments")
    public ApiResponse<PageResponse<CommentDto>> listComments(@PathVariable String id,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(PageResponse.from(feedService.listComments(id, page, size)));
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentDto> addComment(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                               @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.ok(feedService.addComment(principal.id(), id, request));
    }

    @PostMapping("/attachments")
    public ApiResponse<AttachmentRef> uploadAttachment(@RequestParam("file") MultipartFile file, HttpServletRequest httpRequest) {
        String baseUrl = ServletUriComponentsBuilder.fromRequestUri(httpRequest)
                .replacePath(null)
                .build()
                .toUriString();
        return ApiResponse.ok(feedService.uploadAttachment(file, baseUrl));
    }
}
