package com.nukkad.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Blunt but effective per-IP rate limiting for the sensitive, unauthenticated auth endpoints
 * (register, login, refresh, Google sign-in/link, verification/reset email requests) — these are
 * the classic brute-force / account-creation / email-bombing abuse vectors. Single-instance
 * in-memory only (see {@link RateLimiter}).
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private record Limit(int max, Duration window) {}

    private static final Map<String, Limit> LIMITS = Map.of(
            "/api/auth/register", new Limit(5, Duration.ofHours(1)),
            "/api/auth/login", new Limit(10, Duration.ofMinutes(15)),
            "/api/auth/refresh", new Limit(30, Duration.ofMinutes(15)),
            "/api/auth/google", new Limit(20, Duration.ofMinutes(15)),
            "/api/auth/google/code", new Limit(20, Duration.ofMinutes(15)),
            "/api/auth/google/link", new Limit(10, Duration.ofHours(1)),
            "/api/auth/resend-verification", new Limit(5, Duration.ofHours(1)),
            "/api/auth/password-reset/request", new Limit(5, Duration.ofHours(1))
    );

    private final RateLimiter rateLimiter;

    public AuthRateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        Limit limit = LIMITS.get(request.getRequestURI());
        if (limit != null) {
            String key = request.getRequestURI() + ":" + request.getRemoteAddr();
            if (!rateLimiter.tryAcquire(key, limit.max(), limit.window().toMillis())) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(JsonErrorBody.of(
                        "Too many requests. Please try again later.", "RATE_LIMITED", request.getRequestURI()));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
