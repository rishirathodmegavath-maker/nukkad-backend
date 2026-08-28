package com.nukkad.auth.controller;

import com.nukkad.auth.dto.AuthResponse;
import com.nukkad.auth.dto.ChangePasswordRequest;
import com.nukkad.auth.dto.GoogleAuthRequest;
import com.nukkad.auth.dto.LoginRequest;
import com.nukkad.auth.dto.MessageResponse;
import com.nukkad.auth.dto.PasswordResetConfirmDto;
import com.nukkad.auth.dto.PasswordResetRequestDto;
import com.nukkad.auth.dto.RefreshRequest;
import com.nukkad.auth.dto.RefreshTokenResponse;
import com.nukkad.auth.dto.RegisterRequest;
import com.nukkad.auth.service.AuthService;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.nukkad.auth.dto.SessionDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.register(request, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/google")
    public ApiResponse<AuthResponse> google(@Valid @RequestBody GoogleAuthRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.loginWithGoogle(request.idToken(), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.refresh(request.refreshToken(), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent")));
    }

    @GetMapping("/sessions")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<SessionDto>> sessions(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @RequestHeader(value = "X-Refresh-Token", required = false) String currentRefreshToken) {
        return ApiResponse.ok(authService.listSessions(principal.id(), currentRefreshToken));
    }

    @DeleteMapping("/sessions/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Void> revokeSession(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        authService.revokeSession(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/sessions/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Void> logoutAllOtherSessions(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @RequestHeader(value = "X-Refresh-Token", required = false) String currentRefreshToken) {
        authService.revokeAllExcept(principal.id(), currentRefreshToken);
        return ApiResponse.ok(null);
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<UserDto> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.getCurrentUser(principal.id()));
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<MessageResponse> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.id(), request.currentPassword(), request.newPassword());
        return ApiResponse.ok(new MessageResponse("Password changed. Please log in again."));
    }

    @PostMapping("/password-reset/request")
    public ApiResponse<MessageResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDto request) {
        authService.requestPasswordReset(request.email());
        return ApiResponse.ok(new MessageResponse("If that email exists, a reset link was sent"));
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<MessageResponse> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmDto request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
        return ApiResponse.ok(new MessageResponse("Password updated"));
    }
}
