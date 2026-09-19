package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminUserDto;
import com.nukkad.admin.dto.UpdateUserRoleRequest;
import com.nukkad.admin.dto.UpdateUserStatusRequest;
import com.nukkad.admin.service.AdminUserService;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
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

/** The acting admin is always {@code principal.id()} from the verified JWT — never a client-
 *  supplied actorId/adminId. Target users are addressed only by the {id} path variable. */
@RestController
@RequestMapping("/api/admin/users")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminUserDto>> list(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) String role,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(adminUserService.listUsers(q, role, status, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserDto> get(@PathVariable String id) {
        return ApiResponse.ok(adminUserService.getUser(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminUserDto> updateStatus(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @PathVariable String id,
                                                   @Valid @RequestBody UpdateUserStatusRequest request,
                                                   HttpServletRequest httpRequest) {
        var command = new AdminUserService.UpdateUserStatusCommand(request.status(), request.reason());
        return ApiResponse.ok(adminUserService.updateStatus(principal.id(), id, command, httpRequest.getRemoteAddr()));
    }

    @PatchMapping("/{id}/role")
    public ApiResponse<AdminUserDto> updateRole(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable String id,
                                                 @Valid @RequestBody UpdateUserRoleRequest request,
                                                 HttpServletRequest httpRequest) {
        var command = new AdminUserService.UpdateUserRoleCommand(request.role(), request.grant());
        return ApiResponse.ok(adminUserService.updateRole(principal.id(), id, command, httpRequest.getRemoteAddr()));
    }
}
