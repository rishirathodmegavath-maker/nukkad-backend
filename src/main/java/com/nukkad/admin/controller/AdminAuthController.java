package com.nukkad.admin.controller;

import com.nukkad.auth.dto.AdminAuthResponse;
import com.nukkad.auth.dto.AdminIdentity;
import com.nukkad.auth.dto.LoginRequest;
import com.nukkad.auth.dto.RefreshRequest;
import com.nukkad.auth.dto.RefreshTokenResponse;
import com.nukkad.auth.service.AuthService;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in for the separate admin portal. login/refresh/logout are public in SecurityConfig (they
 * authenticate by credentials / refresh token); {@code /me} requires an admin-scoped access token
 * like every other /api/admin/** endpoint.
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminAuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.adminLogin(
                request.email(), request.password(), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.adminRefresh(
                request.refreshToken(), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<AdminIdentity> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(authService.adminIdentity(principal.id()));
    }
}
