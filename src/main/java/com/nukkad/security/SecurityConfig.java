package com.nukkad.security;

import com.nukkad.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/google",
            "/api/auth/google/code",
            "/api/auth/verify-email",
            "/api/auth/resend-verification",
            "/api/auth/refresh",
            "/api/auth/password-reset/**",
            // The admin portal's own sign-in. Identity is proven by credentials / the opaque refresh
            // token in the body, so these can't require a bearer token; everything else under
            // /api/admin/** (including /api/admin/auth/me) does.
            "/api/admin/auth/login",
            "/api/admin/auth/refresh",
            "/api/admin/auth/logout",
            // Forgot-password: the caller is by definition locked out, so no bearer token. The
            // change-password endpoint is deliberately NOT here — it needs an admin-scoped token.
            "/api/admin/auth/password-reset/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health",
            "/ws/**"
    };

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final CorsProperties corsProperties;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final RateLimiter rateLimiter;

    public SecurityConfig(JwtService jwtService,
                           UserRepository userRepository,
                           CorsProperties corsProperties,
                           RestAuthenticationEntryPoint authenticationEntryPoint,
                           RestAccessDeniedHandler accessDeniedHandler,
                           RateLimiter rateLimiter) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.corsProperties = corsProperties;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.rateLimiter = rateLimiter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // X-Content-Type-Options, X-Frame-Options and (now that forward-headers-strategy
                // correctly reports HTTPS behind Railway's proxy) HSTS are already on by default —
                // only Referrer-Policy needs an explicit opt-in on top of that default set.
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Must come first: a CORS preflight carries no Authorization header by
                        // definition (browsers never attach it to an OPTIONS preflight), so without
                        // this the catch-all anyRequest() rule below denies it before Spring's own
                        // CorsFilter/DefaultCorsProcessor ever gets to answer it — producing exactly
                        // "preflight doesn't pass access control check: no Access-Control-Allow-Origin
                        // header", on every single cross-origin request, regardless of how
                        // CORS_ALLOWED_ORIGINS is set. Harmless to permit broadly: CorsUtils
                        // .isPreFlightRequest only matches an actual OPTIONS preflight (OPTIONS +
                        // Access-Control-Request-Method header), never a real request, and the CORS
                        // configuration itself (corsConfigurationSource() below) still fully controls
                        // which origins/methods/headers are actually allowed.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Narrow, method-scoped exception so a PUBLIC-visibility startup can be
                        // viewed via a direct link or discovery without logging in. Scoped to
                        // exactly these two GET shapes — sub-resources (members, materials,
                        // updates...) and every write path stay fully authenticated as before.
                        // StartupService still enforces per-startup visibility for the anonymous
                        // case; this only decides whether the request reaches the controller.
                        .requestMatchers(HttpMethod.GET, "/api/startups", "/api/startups/*").permitAll()
                        // Sole enforcement point for every /api/admin/** endpoint, present or future —
                        // deliberately not left to per-controller annotations, which could be forgotten
                        // on a new endpoint. Frontend route guards are UX only; this is the real boundary.
                        // Requires BOTH the role and an admin-portal token: a member token (even one
                        // belonging to an admin account) can never reach these.
                        .requestMatchers("/api/admin/**")
                                .hasAllAuthorities("ROLE_ADMIN", JwtAuthenticationFilter.SCOPE_ADMIN)
                        // The mirror image: the member application only accepts member tokens, so an
                        // admin-portal session cannot read or act on any member-facing endpoint
                        // (feed, profiles, messages, wallet, ...).
                        .anyRequest().hasAuthority(JwtAuthenticationFilter.SCOPE_APP))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new AuthRateLimitFilter(rateLimiter), JwtAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(corsProperties.allowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // X-Wallet-Token: the short-lived proof that the wallet PIN was just entered (see WalletPinCrypto).
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Refresh-Token", "X-Wallet-Token"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
