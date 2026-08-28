package com.nukkad.resource.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
                                                          @RequestParam(required = false) String chapterId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(resourceService.listResources(q, type, chapterId, principal.id(), page, size)));
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
                                             @RequestParam(required = false) String url,
                                             @RequestParam(required = false) MultipartFile file,
                                             @RequestParam(required = false) String chapterId,
                                             @RequestParam(required = false) String tags,
                                             HttpServletRequest httpRequest) {
        String baseUrl = ServletUriComponentsBuilder.fromRequestUri(httpRequest).replacePath(null).build().toUriString();
        return ApiResponse.ok(resourceService.createResource(principal.id(), title, description, type, url, file, chapterId, parseTags(tags), baseUrl));
    }

    @PutMapping("/{id}")
    public ApiResponse<ResourceDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @PathVariable String id,
                                             @Valid @RequestBody UpdateResourceRequest request) {
        return ApiResponse.ok(resourceService.updateResource(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        resourceService.deleteResource(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/save")
    public ApiResponse<Map<String, Object>> toggleSave(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        boolean saved = resourceService.toggleSave(principal.id(), id);
        return ApiResponse.ok(Map.of("saved", saved));
    }

    private Set<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return Set.of();
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toSet());
    }
}
