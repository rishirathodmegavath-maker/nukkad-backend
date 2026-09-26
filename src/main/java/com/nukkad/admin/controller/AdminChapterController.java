package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminCreateChapterRequest;
import com.nukkad.chapter.dto.ChapterCoverImageDto;
import com.nukkad.chapter.dto.ChapterDto;
import com.nukkad.chapter.dto.UpdateChapterRequest;
import com.nukkad.chapter.service.ChapterService;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/**
 * The only place chapters are created by an admin. Everything under /api/admin/** already requires
 * an admin role AND an admin-portal token (see SecurityConfig), so a member — even one whose account
 * is an admin — can never reach these endpoints. This is a separate list/create/update surface from
 * the member-facing {@code ChapterController} (not a wrapper around it): an admin-portal token cannot
 * call any member-facing endpoint either, per SecurityConfig's own {@code anyRequest().hasAuthority
 * (SCOPE_APP)} catch-all, so the cover/logo pre-upload endpoints are mirrored here too.
 */
@RestController
@RequestMapping("/api/admin/chapters")
@SecurityRequirement(name = "bearerAuth")
public class AdminChapterController {

    private final ChapterService chapterService;

    public AdminChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ChapterDto>> list(@RequestParam(required = false) String q,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(chapterService.listChapters(q, null, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChapterDto> get(@PathVariable String id) {
        return ApiResponse.ok(chapterService.getChapter(id));
    }

    @PostMapping("/cover-images")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChapterCoverImageDto> uploadCoverImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(chapterService.uploadCoverImage(file));
    }

    @PostMapping("/logo-images")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChapterCoverImageDto> uploadLogoImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(chapterService.uploadLogoImage(file));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChapterDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody AdminCreateChapterRequest request,
                                           HttpServletRequest httpRequest) {
        return ApiResponse.ok(chapterService.createChapterAsAdmin(principal.id(), request, httpRequest.getRemoteAddr()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChapterDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @PathVariable String id,
                                           @Valid @RequestBody UpdateChapterRequest request,
                                           HttpServletRequest httpRequest) {
        return ApiResponse.ok(chapterService.updateChapterAsAdmin(principal.id(), id, request, httpRequest.getRemoteAddr()));
    }
}
