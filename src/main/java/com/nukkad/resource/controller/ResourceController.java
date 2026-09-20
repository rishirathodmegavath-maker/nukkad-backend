package com.nukkad.resource.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.resource.dto.ResourceDto;
import com.nukkad.resource.service.ResourceService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Member-facing resource library — deliberately READ-ONLY. Resources are curated by admins (see
 * AdminResourceController under /api/admin/resources), so the only things a member can do here are
 * browse, open, download and save one for themselves. There must be no create/update/delete mapping on
 * this controller; ResourceControllerReadOnlyTest fails the build if one is ever added.
 */
@RestController
@RequestMapping("/api/resources")
@SecurityRequirement(name = "bearerAuth")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
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
        return ApiResponse.ok(PageResponse.from(resourceService.listResources(q, type, category, featured, chapterId, principal.id(), page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResourceDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(resourceService.getResource(id, principal.id()));
    }

    /**
     * Forces a real download (Content-Disposition: attachment). Opening a file in the browser needs no
     * endpoint at all — the file's own URL is loaded directly and shown inline — but a browser ignores
     * the HTML "download" attribute for a cross-origin URL, so a true download has to come from here.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String id) {
        ResourceService.Download download = resourceService.openDownload(id);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(parseMediaType(download.contentType()));
        if (download.contentLength() >= 0) response.contentLength(download.contentLength());
        return response.body(new InputStreamResource(download.stream()));
    }

    @PostMapping("/{id}/save")
    public ApiResponse<Map<String, Object>> toggleSave(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        boolean saved = resourceService.toggleSave(principal.id(), id);
        return ApiResponse.ok(Map.of("saved", saved));
    }

    private static MediaType parseMediaType(String contentType) {
        try {
            return contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
