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
import com.nukkad.startup.dto.StartupRoleDto;
import com.nukkad.startup.dto.StartupTeamMemberDto;
import com.nukkad.startup.dto.StartupUpdateDto;
import com.nukkad.startup.dto.UpdateStartupRequest;
import com.nukkad.startup.service.StartupService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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
        return ApiResponse.ok(PageResponse.from(
                startupService.listStartups(q, sector, stage, isRaising, chapterId, memberId, principal.id(), page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<StartupDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(startupService.getStartup(id, principal.id()));
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
                                                @RequestParam("file") MultipartFile file,
                                                HttpServletRequest httpRequest) {
        String baseUrl = ServletUriComponentsBuilder.fromRequestUri(httpRequest)
                .replacePath(null)
                .build()
                .toUriString();
        return ApiResponse.ok(startupService.updateLogo(principal.id(), id, file, baseUrl));
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
}
