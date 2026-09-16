package com.nukkad.startup.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.startup.dto.AddTeamMemberRequest;
import com.nukkad.startup.dto.CreateStartupRequest;
import com.nukkad.startup.dto.CreateStartupRoleRequest;
import com.nukkad.startup.dto.JoinStartupRequest;
import com.nukkad.startup.dto.PostStartupUpdateRequest;
import com.nukkad.startup.dto.StartupDto;
import com.nukkad.startup.dto.StartupJoinRequestDto;
import com.nukkad.startup.dto.StartupMaterialDto;
import com.nukkad.startup.dto.StartupRoleDto;
import com.nukkad.startup.dto.StartupTeamMemberDto;
import com.nukkad.startup.dto.StartupUpdateDto;
import com.nukkad.startup.dto.UpdateStartupRequest;
import com.nukkad.startup.service.StartupService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import java.util.Map;

@RestController
@RequestMapping("/api/startups")
@SecurityRequirement(name = "bearerAuth")
public class StartupController {

    private final StartupService startupService;

    public StartupController(StartupService startupService) {
        this.startupService = startupService;
    }

    // list() and get() are reachable without authentication (see SecurityConfig) so that a PUBLIC
    // startup can be viewed via a direct link or discovery without logging in — `principal` is
    // null for an anonymous caller, and StartupService uses that to restrict results/access to
    // PUBLIC startups only. Every other endpoint on this controller stays fully authenticated.
    @GetMapping
    public ApiResponse<PageResponse<StartupDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(required = false) String sector,
                                                        @RequestParam(required = false) String stage,
                                                        @RequestParam(required = false) Boolean isRaising,
                                                        @RequestParam(required = false) String chapterId,
                                                        @RequestParam(required = false) String memberId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        String viewerId = principal == null ? null : principal.id();
        return ApiResponse.ok(PageResponse.from(
                startupService.listStartups(q, sector, stage, isRaising, chapterId, memberId, viewerId, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<StartupDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        String viewerId = principal == null ? null : principal.id();
        return ApiResponse.ok(startupService.getStartup(id, viewerId));
    }

    @GetMapping("/me/founding")
    public ApiResponse<List<StartupDto>> myFoundedStartups(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(startupService.listMyFoundedStartups(principal.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StartupDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody CreateStartupRequest request) {
        return ApiResponse.ok(startupService.createStartup(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<StartupDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @PathVariable String id,
                                           @Valid @RequestBody UpdateStartupRequest request) {
        return ApiResponse.ok(startupService.updateStartup(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        startupService.deleteStartup(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/logo")
    public ApiResponse<StartupDto> uploadLogo(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable String id,
                                                @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(startupService.updateLogo(principal.id(), id, file));
    }

    @DeleteMapping("/{id}/logo")
    public ApiResponse<StartupDto> removeLogo(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(startupService.removeLogo(principal.id(), id));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<StartupTeamMemberDto>> members(@PathVariable String id) {
        return ApiResponse.ok(startupService.getMembers(id));
    }

    @GetMapping("/{id}/my-membership")
    public ApiResponse<StartupTeamMemberDto> myMembership(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @PathVariable String id) {
        return ApiResponse.ok(startupService.getMyMembership(principal.id(), id));
    }

    @GetMapping("/{id}/join-requests")
    public ApiResponse<List<StartupJoinRequestDto>> joinRequests(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                   @PathVariable String id) {
        return ApiResponse.ok(startupService.getJoinRequests(principal.id(), id));
    }

    @PostMapping("/{id}/join")
    public ApiResponse<StartupTeamMemberDto> join(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable String id,
                                                    @RequestBody(required = false) JoinStartupRequest request) {
        String roleId = request == null ? null : request.roleId();
        String message = request == null ? null : request.message();
        return ApiResponse.ok(startupService.requestToJoin(principal.id(), id, roleId, message));
    }

    @PostMapping("/{id}/leave")
    public ApiResponse<Void> leave(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        startupService.leaveTeam(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StartupTeamMemberDto> addMember(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @PathVariable String id,
                                                          @Valid @RequestBody AddTeamMemberRequest request) {
        return ApiResponse.ok(startupService.addMember(principal.id(), id, request.userId(), request.roleId()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable String id,
                                            @PathVariable String userId) {
        startupService.removeMember(principal.id(), id, userId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/join-requests/{memberId}/accept")
    public ApiResponse<StartupTeamMemberDto> acceptJoinRequest(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @PathVariable String memberId) {
        return ApiResponse.ok(startupService.acceptJoinRequest(principal.id(), memberId));
    }

    @PostMapping("/join-requests/{memberId}/reject")
    public ApiResponse<StartupTeamMemberDto> rejectJoinRequest(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @PathVariable String memberId) {
        return ApiResponse.ok(startupService.rejectJoinRequest(principal.id(), memberId));
    }

    @PostMapping("/{id}/follow")
    public ApiResponse<Map<String, Object>> follow(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        var result = startupService.toggleFollow(principal.id(), id);
        return ApiResponse.ok(Map.of("following", result.following()));
    }

    @PostMapping("/{id}/updates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StartupUpdateDto> postUpdate(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable String id,
                                                      @Valid @RequestBody PostStartupUpdateRequest request) {
        return ApiResponse.ok(startupService.postUpdate(principal.id(), id, request.content()));
    }

    @PostMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StartupRoleDto> createRole(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable String id,
                                                    @Valid @RequestBody CreateStartupRoleRequest request) {
        return ApiResponse.ok(startupService.createRole(principal.id(), id, request));
    }

    @GetMapping("/{id}/updates")
    public ApiResponse<List<StartupUpdateDto>> updates(@PathVariable String id) {
        return ApiResponse.ok(startupService.getUpdates(id));
    }

    @GetMapping("/{id}/roles")
    public ApiResponse<List<StartupRoleDto>> roles(@PathVariable String id) {
        return ApiResponse.ok(startupService.getRoles(id));
    }

    @GetMapping("/{id}/materials")
    public ApiResponse<List<StartupMaterialDto>> materials(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(startupService.getMaterials(id, principal.id()));
    }

    @PostMapping(value = "/{id}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StartupMaterialDto> addMaterial(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @PathVariable String id,
                                                          @RequestParam String materialType,
                                                          @RequestParam(required = false) String title,
                                                          @RequestParam(required = false) String url,
                                                          @RequestParam(required = false) MultipartFile file) {
        return ApiResponse.ok(startupService.addMaterial(principal.id(), id, materialType, title, url, file));
    }

    @PutMapping(value = "/{id}/materials/{materialId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StartupMaterialDto> updateMaterial(@AuthenticationPrincipal AuthenticatedUser principal,
                                                             @PathVariable String id,
                                                             @PathVariable String materialId,
                                                             @RequestParam(required = false) String title,
                                                             @RequestParam(required = false) String url,
                                                             @RequestParam(required = false) MultipartFile file) {
        return ApiResponse.ok(startupService.updateMaterial(principal.id(), materialId, title, url, file));
    }

    @DeleteMapping("/{id}/materials/{materialId}")
    public ApiResponse<Void> deleteMaterial(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable String id,
                                              @PathVariable String materialId) {
        startupService.deleteMaterial(principal.id(), materialId);
        return ApiResponse.ok(null);
    }
}
