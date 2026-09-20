package com.nukkad.admin.controller;

import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.resource.dto.ResourceChapterOption;
import com.nukkad.resource.dto.ResourceDto;
import com.nukkad.resource.dto.UpdateResourceRequest;
import com.nukkad.resource.service.ResourceService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The only place resources are created, edited or deleted. Everything under /api/admin/** already
 * requires an admin role AND an admin-portal token (see SecurityConfig), so a member — even one whose
 * account is an admin — can never reach these endpoints. Mirrors the other Admin*Controllers: it
 * reuses ResourceService, with no resource business logic duplicated here.
 */
@RestController
@RequestMapping("/api/admin/resources")
@SecurityRequirement(name = "bearerAuth")
public class AdminResourceController {

    private final ResourceService resourceService;

    public AdminResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ResourceDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(required = false) String type,
                                                          @RequestParam(required = false) String category,
                                                          @RequestParam(required = false) Boolean featured,
                                                          @RequestParam(required = false) String chapterId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                resourceService.listResources(q, type, category, featured, chapterId, principal.id(), page, AdminPaging.clampSize(size))));
    }

    /** Chapters for the form's chapter picker. A literal path, so it wins over "/{id}". */
    @GetMapping("/chapter-options")
    public ApiResponse<List<ResourceChapterOption>> chapterOptions() {
        return ApiResponse.ok(resourceService.listChapterOptions());
    }

    @GetMapping("/{id}")
    public ApiResponse<ResourceDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(resourceService.getResource(id, principal.id()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ResourceDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @RequestParam String title,
                                             @RequestParam(required = false) String description,
                                             @RequestParam String type,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) String provider,
                                             @RequestParam(required = false) Integer durationMinutes,
                                             @RequestParam(defaultValue = "false") boolean featured,
                                             @RequestParam(required = false) String url,
                                             @RequestParam(required = false) MultipartFile file,
                                             @RequestParam(required = false) MultipartFile thumbnail,
                                             @RequestParam(required = false) String chapterId,
                                             @RequestParam(required = false) String tags,
                                             HttpServletRequest httpRequest) {
        ResourceService.NewResource input = new ResourceService.NewResource(title, description, type, url, category,
                provider, durationMinutes, featured, chapterId, parseTags(tags));
        return ApiResponse.ok(resourceService.createResource(principal.id(), input, file, thumbnail, httpRequest.getRemoteAddr()));
    }

    /** Sets or replaces the card image (an uploaded PNG/JPEG/WEBP/GIF). */
    @PostMapping(value = "/{id}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResourceDto> replaceThumbnail(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @PathVariable String id,
                                                       @RequestParam MultipartFile image,
                                                       HttpServletRequest httpRequest) {
        return ApiResponse.ok(resourceService.replaceThumbnail(principal.id(), id, image, httpRequest.getRemoteAddr()));
    }

    @DeleteMapping("/{id}/thumbnail")
    public ApiResponse<ResourceDto> removeThumbnail(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable String id,
                                                      HttpServletRequest httpRequest) {
        return ApiResponse.ok(resourceService.removeThumbnail(principal.id(), id, httpRequest.getRemoteAddr()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ResourceDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @PathVariable String id,
                                             @Valid @RequestBody UpdateResourceRequest request,
                                             HttpServletRequest httpRequest) {
        return ApiResponse.ok(resourceService.updateResource(principal.id(), id, request, httpRequest.getRemoteAddr()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                     @PathVariable String id,
                                     HttpServletRequest httpRequest) {
        resourceService.deleteResource(principal.id(), id, httpRequest.getRemoteAddr());
        return ApiResponse.ok(null);
    }

    private Set<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return Set.of();
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toSet());
    }
}
