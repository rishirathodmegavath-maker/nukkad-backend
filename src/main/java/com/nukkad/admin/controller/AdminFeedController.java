package com.nukkad.admin.controller;

import com.nukkad.admin.dto.SetContentRemovedRequest;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.feed.dto.PostDto;
import com.nukkad.feed.service.FeedService;
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

/** Reactive-only moderation for Feed posts — no pre-publish queue (see FeedService.setRemovedByAdmin
 *  for why), just the same admin takedown/restore lever Idea/Startup/Opportunity already have. */
@RestController
@RequestMapping("/api/admin/feed/posts")
@SecurityRequirement(name = "bearerAuth")
public class AdminFeedController {

    private final FeedService feedService;

    public AdminFeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PostDto>> list(@RequestParam(defaultValue = "false") boolean includeRemoved,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(feedService.listForAdmin(includeRemoved, page, AdminPaging.clampSize(size))));
    }

    @GetMapping("/{id}")
    public ApiResponse<PostDto> get(@PathVariable String id) {
        return ApiResponse.ok(feedService.getForAdmin(id));
    }

    @PatchMapping("/{id}/removed")
    public ApiResponse<PostDto> setRemoved(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable String id,
                                            @Valid @RequestBody SetContentRemovedRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(feedService.setRemovedByAdmin(
                principal.id(), id, request.removed(), request.reason(), httpRequest.getRemoteAddr()));
    }
}
