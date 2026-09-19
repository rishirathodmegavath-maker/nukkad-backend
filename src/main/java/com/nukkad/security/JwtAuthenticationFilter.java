package com.nukkad.security;

import com.nukkad.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    public static final String SCOPE_ADMIN = "SCOPE_ADMIN";
    public static final String SCOPE_APP = "SCOPE_APP";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                var claims = jwtService.parseAndValidate(token);
                AuthenticatedUser principal = jwtService.toAuthenticatedUser(claims);

                // The one DB round trip that makes suspension/disable invalidate an already-issued,
                // still-unexpired access token immediately rather than waiting out its TTL: a
                // single indexed scalar lookup by primary key, as cheap as a per-request check can
                // be. A missing row (deleted account) or a version that no longer matches what was
                // embedded in the token both fail closed — same as an invalid/expired JWT.
                Integer currentTokenVersion = userRepository.findTokenVersionById(principal.id()).orElse(null);
                if (currentTokenVersion == null || currentTokenVersion != principal.tokenVersion()) {
                    log.debug("Rejected access token with stale or unknown token version for user {}", principal.id());
                    SecurityContextHolder.clearContext();
                } else {
                    // Exactly one scope authority per token: admin-portal tokens get SCOPE_ADMIN,
                    // every member token (including ones issued before scopes existed) SCOPE_APP.
                    // SecurityConfig uses it to keep the two audiences fully separate.
                    List<GrantedAuthority> authorities = new java.util.ArrayList<>(principal.roles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .map(GrantedAuthority.class::cast)
                            .toList());
                    authorities.add(new SimpleGrantedAuthority(jwtService.isAdminScope(claims) ? SCOPE_ADMIN : SCOPE_APP));
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejected invalid access token: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
