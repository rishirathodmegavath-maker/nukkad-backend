package com.nukkad.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
        String token = jwtService.issueAccessToken("user-1", "user@example.com", Set.of("USER", "FOUNDER"));

        var claims = jwtService.parseAndValidate(token);
        AuthenticatedUser user = jwtService.toAuthenticatedUser(claims);

        assertThat(user.id()).isEqualTo("user-1");
        assertThat(user.email()).isEqualTo("user@example.com");
        assertThat(user.roles()).containsExactlyInAnyOrder("USER", "FOUNDER");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.issueAccessToken("user-1", "user@example.com", Set.of("USER"));
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parseAndValidate(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtProperties shortLived = new JwtProperties(properties.secret(), 0L, 604800L);
        JwtService shortLivedService = new JwtService(shortLived);
        String token = shortLivedService.issueAccessToken("user-1", "user@example.com", Set.of("USER"));

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
