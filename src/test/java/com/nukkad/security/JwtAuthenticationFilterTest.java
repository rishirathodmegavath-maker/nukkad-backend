package com.nukkad.security;

import com.nukkad.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The core of the immediate-revocation fix: an access token's embedded "tv" (token version) claim
 * must be compared against the user's *current* server-side value on every request, not just
 * trusted because the signature and expiry check out.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private final JwtProperties properties = new JwtProperties(
            "test-secret-key-that-is-long-enough-for-hmac-sha-512-signing-xxxxxxxxxxxxxxxx", 900L, 604800L);
    private final JwtService jwtService = new JwtService(properties);

    @Mock private UserRepository userRepository;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void withBearerToken(String token) {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
    }

    @Test
    void authenticatesWhenTokenVersionMatchesCurrentValue() throws Exception {
        String token = jwtService.issueAccessToken("user-1", "user@example.com", Set.of("USER"), 2);
        withBearerToken(token);
        when(userRepository.findTokenVersionById("user-1")).thenReturn(Optional.of(2));

        filter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(((AuthenticatedUser) auth.getPrincipal()).id()).isEqualTo("user-1");
        verify(filterChain).doFilter(request, response);
    }

    private java.util.Set<String> authorityNames() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void anAdminPortalTokenGetsTheAdminScopeAndNeverTheMemberScope() throws Exception {
        withBearerToken(jwtService.issueAdminAccessToken("admin-1", "a@example.com", Set.of("USER", "ADMIN"), 0));
        when(userRepository.findTokenVersionById("admin-1")).thenReturn(Optional.of(0));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(authorityNames()).contains("ROLE_ADMIN", JwtAuthenticationFilter.SCOPE_ADMIN)
                .doesNotContain(JwtAuthenticationFilter.SCOPE_APP);
    }

    @Test
    void aMemberTokenGetsTheMemberScopeEvenIfItsAccountIsAnAdmin() throws Exception {
        // An admin account's ordinary (non-portal) token: it has the ROLE but not the admin scope,
        // so SecurityConfig's admin rule (role AND scope) still rejects it.
        withBearerToken(jwtService.issueAccessToken("admin-1", "a@example.com", Set.of("USER", "ADMIN"), 0));
        when(userRepository.findTokenVersionById("admin-1")).thenReturn(Optional.of(0));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(authorityNames()).contains("ROLE_ADMIN", JwtAuthenticationFilter.SCOPE_APP)
                .doesNotContain(JwtAuthenticationFilter.SCOPE_ADMIN);
    }

    @Test
    void aTokenIssuedBeforeScopesExistedIsTreatedAsAMemberToken() throws Exception {
        // Same shape jwtService.issueAccessToken always produced: no "scp" claim at all.
        withBearerToken(jwtService.issueAccessToken("user-1", "u@example.com", Set.of("USER"), 0));
        when(userRepository.findTokenVersionById("user-1")).thenReturn(Optional.of(0));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(authorityNames()).contains(JwtAuthenticationFilter.SCOPE_APP)
                .doesNotContain(JwtAuthenticationFilter.SCOPE_ADMIN);
    }

    @Test
    void rejectsTokenIssuedBeforeAnAdminSuspendedTheAccount() throws Exception {
        // Token was minted while the user's version was 1 (i.e. before any admin action).
        String token = jwtService.issueAccessToken("user-1", "user@example.com", Set.of("USER"), 1);
        withBearerToken(token);
        // Admin has since suspended the account, bumping the live version to 2.
        when(userRepository.findTokenVersionById("user-1")).thenReturn(Optional.of(2));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response); // request still proceeds — as anonymous
    }

    @Test
    void rejectsTokenIssuedBeforeAnAdminDisabledTheAccount() throws Exception {
        String token = jwtService.issueAccessToken("user-1", "user@example.com", Set.of("USER"), 4);
        withBearerToken(token);
        when(userRepository.findTokenVersionById("user-1")).thenReturn(Optional.of(5));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsTokenForAUserThatNoLongerExists() throws Exception {
        String token = jwtService.issueAccessToken("deleted-user", "user@example.com", Set.of("USER"), 0);
        withBearerToken(token);
        when(userRepository.findTokenVersionById("deleted-user")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void reactivationsNewTokenAuthenticatesButAnyOldPreSuspensionTokenStillDoesNot() throws Exception {
        // Old token minted at version 1, before suspend (->2) and reactivate (->3).
        String staleToken = jwtService.issueAccessToken("user-1", "user@example.com", Set.of("USER"), 1);
        // Fresh token minted at login time, after reactivation, embedding the current version 3.
        String freshToken = jwtService.issueAccessToken("user-1", "user@example.com", Set.of("USER"), 3);
        when(userRepository.findTokenVersionById("user-1")).thenReturn(Optional.of(3));

        withBearerToken(staleToken);
        filter.doFilterInternal(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        SecurityContextHolder.clearContext();
        withBearerToken(freshToken);
        filter.doFilterInternal(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void rejectsAnOldTokenThatStillCarriesTheAdminRoleAfterAdminWasRevoked() throws Exception {
        // Token was minted while this user was ADMIN, at version 1. An admin has since revoked
        // their ADMIN role, which bumps the live version to 2 (see AdminUserService.updateRole).
        // Even though this token's "roles" claim still says ADMIN, it must not authenticate at
        // all -- so it can never reach the hasRole("ADMIN") check on /api/admin/** in the first
        // place, regardless of what roles it claims to carry.
        String staleAdminToken = jwtService.issueAccessToken("user-1", "user@example.com", Set.of("USER", "ADMIN"), 1);
        withBearerToken(staleAdminToken);
        when(userRepository.findTokenVersionById("user-1")).thenReturn(Optional.of(2));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsTamperedTokenWithoutConsultingTheDatabase() throws Exception {
        String token = jwtService.issueAccessToken("user-1", "user@example.com", Set.of("USER"), 0);
        withBearerToken(token.substring(0, token.length() - 2) + "xx");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    void skipsEntirelyWhenNoAuthorizationHeaderIsPresent() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userRepository);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotSetAuthenticationWhenTokenVersionClaimIsMissingButCurrentVersionIsNonZero() throws Exception {
        // Simulates a token from before this feature existed: no "tv" claim -> read as 0 (see
        // JwtServiceTest). If the account has since had ANY admin status change (version > 0),
        // that old-format token must still fail closed.
        var claims = io.jsonwebtoken.Jwts.builder()
                .subject("user-1")
                .claim("email", "user@example.com")
                .claim("roles", Set.of("USER"))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        properties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();
        withBearerToken(claims);
        when(userRepository.findTokenVersionById("user-1")).thenReturn(Optional.of(1));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
