package com.nukkad.chapter.controller;

import com.nukkad.chapter.dto.ChapterDto;
import com.nukkad.chapter.dto.CreateChapterRequest;
import com.nukkad.chapter.dto.UpdateChapterRequest;
import com.nukkad.chapter.service.ChapterService;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.security.AuthenticatedUser;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chapters")
@SecurityRequirement(name = "bearerAuth")
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ChapterDto>> list(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) String presidentUserId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(chapterService.listChapters(q, presidentUserId, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChapterDto> get(@PathVariable String id) {
        return ApiResponse.ok(chapterService.getChapter(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChapterDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody CreateChapterRequest request) {
        return ApiResponse.ok(chapterService.createChapter(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChapterDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @PathVariable String id,
                                           @Valid @RequestBody UpdateChapterRequest request) {
        return ApiResponse.ok(chapterService.updateChapter(principal.id(), id, request));
    }

    @PostMapping("/{id}/cover")
    public ApiResponse<ChapterDto> uploadCover(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable String id,
                                                @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(chapterService.updateCoverImage(principal.id(), id, file));
    }

    @DeleteMapping("/{id}/cover")
    public ApiResponse<ChapterDto> removeCover(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(chapterService.removeCoverImage(principal.id(), id));
    }

    @PostMapping("/{id}/join")
    public ApiResponse<UserDto> join(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(chapterService.joinChapter(principal.id(), id));
    }

    @PostMapping("/{id}/leave")
    public ApiResponse<UserDto> leave(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(chapterService.leaveChapter(principal.id(), id));
    }

    @PostMapping("/{id}/members/{userId}")
    public ApiResponse<UserDto> addMember(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @PathVariable String id, @PathVariable String userId) {
        return ApiResponse.ok(chapterService.addMember(principal.id(), id, userId));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<UserDto> removeMember(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable String id, @PathVariable String userId) {
        return ApiResponse.ok(chapterService.removeMember(principal.id(), id, userId));
    }
}
