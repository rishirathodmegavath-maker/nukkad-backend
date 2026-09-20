package com.nukkad.admin.controller;

import com.nukkad.auth.dto.AdminAuthResponse;
import com.nukkad.auth.dto.AdminIdentity;
import com.nukkad.auth.dto.ChangePasswordRequest;
import com.nukkad.auth.dto.LoginRequest;
import com.nukkad.auth.dto.MessageResponse;
import com.nukkad.auth.dto.PasswordResetConfirmDto;
import com.nukkad.auth.dto.PasswordResetRequestDto;
import com.nukkad.auth.dto.RefreshRequest;
import com.nukkad.auth.dto.RefreshTokenResponse;
import com.nukkad.auth.service.AuthService;
import com.nukkad.common.email.MailProperties;
import com.nukkad.common.exception.ApiException;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sign-in for the separate admin portal. login/refresh/logout are public in SecurityConfig (they
 * authenticate by credentials / refresh token); {@code /me} requires an admin-scoped access token
 * like every other /api/admin/** endpoint.
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AuthService authService;
    private final MailProperties mailProperties;

    public AdminAuthController(AuthService authService, MailProperties mailProperties) {
        this.authService = authService;
        this.mailProperties = mailProperties;
    }

    /** Whether the emailed "forgot password" flow is switched on (ADMIN_PASSWORD_RESET_ENABLED). Public,
     *  because the sign-in page needs it before anyone is signed in; it reveals only that one flag. */
    @GetMapping("/password-reset/status")
    public ApiResponse<Map<String, Boolean>> passwordResetStatus() {
        return ApiResponse.ok(Map.of("enabled", mailProperties.adminPasswordResetEnabled()));
    }

    private void requirePasswordResetEnabled() {
        if (!mailProperties.adminPasswordResetEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PASSWORD_RESET_UNAVAILABLE",
                    "Password reset by email is not switched on yet. Ask the platform owner to reset your password.");
        }
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
        authService.adminLogout(request.refreshToken());
        return ApiResponse.ok(null);
    }

    /** Public (identity is proven by the emailed token later). Always the same response, so it can't
     *  be used to find out which email belongs to an administrator. */
    @PostMapping("/password-reset/request")
    public ApiResponse<MessageResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDto request,
                                                              HttpServletRequest httpRequest) {
        requirePasswordResetEnabled();
        authService.requestAdminPasswordReset(request.email(), httpRequest.getRemoteAddr());
        return ApiResponse.ok(new MessageResponse(
                "If that email belongs to an administrator, a reset link has been sent. It expires in 30 minutes."));
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<MessageResponse> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmDto request,
                                                              HttpServletRequest httpRequest) {
        requirePasswordResetEnabled();
        authService.confirmAdminPasswordReset(request.token(), request.newPassword(), httpRequest.getRemoteAddr());
        return ApiResponse.ok(new MessageResponse("Password updated. Sign in with your new password."));
    }

    /** Requires an admin-scoped token (everything under /api/admin/** except the public paths does). */
    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<MessageResponse> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @Valid @RequestBody ChangePasswordRequest request,
                                                        HttpServletRequest httpRequest) {
        authService.adminChangePassword(principal.id(), request.currentPassword(), request.newPassword(),
                httpRequest.getRemoteAddr());
        return ApiResponse.ok(new MessageResponse("Password changed. Please sign in again."));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<AdminIdentity> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(authService.adminIdentity(principal.id()));
    }
}
