package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminProgramDto;
import com.nukkad.admin.dto.CreateProgramRequest;
import com.nukkad.admin.dto.UpdateProgramRequest;
import com.nukkad.admin.service.AdminProgramService;
import com.nukkad.common.response.ApiResponse;
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

import java.util.List;

/** Program CONTENT management — create, edit, publish/archive/delete. Gated automatically by
 *  SecurityConfig's existing {@code /api/admin/**} matcher (ROLE_ADMIN + SCOPE_ADMIN) — no new
 *  security rule needed. See {@link AdminProgramApplicationController} for the separate applicant
 *  review surface. */
@RestController
@RequestMapping("/api/admin/programs")
@SecurityRequirement(name = "bearerAuth")
public class AdminProgramController {

    private final AdminProgramService adminProgramService;

    public AdminProgramController(AdminProgramService adminProgramService) {
        this.adminProgramService = adminProgramService;
    }

    @GetMapping
    public ApiResponse<List<AdminProgramDto>> list() {
        return ApiResponse.ok(adminProgramService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminProgramDto> get(@PathVariable String id) {
        return ApiResponse.ok(adminProgramService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminProgramDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @Valid @RequestBody CreateProgramRequest request,
                                                 HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminProgramService.create(principal.id(), request, httpRequest.getRemoteAddr()));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminProgramDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable String id,
                                                 @Valid @RequestBody UpdateProgramRequest request,
                                                 HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminProgramService.update(principal.id(), id, request, httpRequest.getRemoteAddr()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                     @PathVariable String id,
                                     HttpServletRequest httpRequest) {
        adminProgramService.delete(principal.id(), id, httpRequest.getRemoteAddr());
        return ApiResponse.ok(null);
    }

    /** Pre-upload: returns a URL the create/update form then carries as a normal field, mirroring
     *  {@code AdminChapterController}'s cover/logo endpoints. */
    @PostMapping(value = "/hero-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageUrl> uploadHeroImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(new ImageUrl(adminProgramService.uploadHeroImage(file)));
    }

    @PostMapping(value = "/thumbnail-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageUrl> uploadThumbnailImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(new ImageUrl(adminProgramService.uploadThumbnail(file)));
    }

    public record ImageUrl(String url) {}
}
