package com.nukkad.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtProperties properties = new JwtProperties(
            "test-secret-key-that-is-long-enough-for-hmac-sha-512-signing-xxxxxxxxxxxxxxxx",
            900L,
            604800L
    );
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void issuesAndParsesAccessTokenWithClaims() {
        String token = jwtService.issueAccessToken("user-1", "user@example.com", Set.of("USER", "FOUNDER"), 3);

        var claims = jwtService.parseAndValidate(token);
        AuthenticatedUser user = jwtService.toAuthenticatedUser(claims);

        assertThat(user.id()).isEqualTo("user-1");
        assertThat(user.email()).isEqualTo("user@example.com");
        assertThat(user.roles()).containsExactlyInAnyOrder("USER", "FOUNDER");
        assertThat(user.tokenVersion()).isEqualTo(3);
    }

    @Test
    void toAuthenticatedUserDefaultsMissingTokenVersionClaimToZero() {
        // A token minted without the "tv" claim at all (e.g. issued by an older build) must not be
        // treated as a parse failure — it's read as version 0, matching the column's default for
        // any account that has never had an admin status change.
        String token = Jwts.builder()
                .subject("user-1")
                .claim("email", "user@example.com")
                .claim("roles", Set.of("USER"))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(properties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        AuthenticatedUser user = jwtService.toAuthenticatedUser(jwtService.parseAndValidate(token));

        assertThat(user.tokenVersion()).isEqualTo(0);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.issueAccessToken("user-1", "user@example.com", Set.of("USER"), 0);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parseAndValidate(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtProperties shortLived = new JwtProperties(properties.secret(), 0L, 604800L);
        JwtService shortLivedService = new JwtService(shortLived);
        String token = shortLivedService.issueAccessToken("user-1", "user@example.com", Set.of("USER"), 0);

        assertThatThrownBy(() -> shortLivedService.parseAndValidate(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void opaqueTokenHashIsDeterministicAndOneWay() {
        String raw = jwtService.generateOpaqueToken();
        String hash1 = jwtService.hashOpaqueToken(raw);
        String hash2 = jwtService.hashOpaqueToken(raw);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(raw);
    }

    @Test
    void generatedOpaqueTokensAreUnique() {
        assertThat(jwtService.generateOpaqueToken()).isNotEqualTo(jwtService.generateOpaqueToken());
    }
}
