package com.nukkad.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JwtService {

    public static final String SCOPE_CLAIM = "scp";
    public static final String ADMIN_SCOPE = "admin";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** {@code tokenVersion} is a snapshot of the user's current server-side token version at
     *  issuance — see {@link com.nukkad.security.JwtAuthenticationFilter} for how it's compared
     *  against the live value on every request to detect a revoked token. */
    public String issueAccessToken(String userId, String email, Set<String> roles, int tokenVersion) {
        return buildAccessToken(userId, email, roles, tokenVersion, null);
    }

    /** Token for the separate admin portal. The "scp" claim is what keeps it out of the member
     *  application (and keeps member tokens out of /api/admin/**): SecurityConfig requires it on
     *  admin endpoints and forbids it everywhere else. */
    public String issueAdminAccessToken(String userId, String email, Set<String> roles, int tokenVersion) {
        return buildAccessToken(userId, email, roles, tokenVersion, ADMIN_SCOPE);
    }

    private String buildAccessToken(String userId, String email, Set<String> roles, int tokenVersion, String scope) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("roles", roles)
                .claim("tv", tokenVersion);
        if (scope != null) {
            builder.claim(SCOPE_CLAIM, scope);
        }
        return builder
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.accessExpirationSeconds())))
                .signWith(signingKey)
                .compact();
    }

    public boolean isAdminScope(Claims claims) {
        return ADMIN_SCOPE.equals(claims.get(SCOPE_CLAIM, String.class));
    }

    public long getAccessExpirationSeconds() {
        return properties.accessExpirationSeconds();
    }

    public Claims parseAndValidate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public AuthenticatedUser toAuthenticatedUser(Claims claims) {
        @SuppressWarnings("unchecked")
        var rolesClaim = (Iterable<String>) claims.get("roles", Iterable.class);
        Set<String> roles = rolesClaim == null
                ? Set.of()
                : java.util.stream.StreamSupport.stream(rolesClaim.spliterator(), false).collect(Collectors.toSet());
        // Number, not Integer: JJWT/Jackson may deserialize a numeric claim as Integer or Long
        // depending on the parser path. A token issued before this claim existed has no "tv" at
        // all — treat that as version 0, matching the column's default for every account that has
        // never had an admin status change, so a same-format-otherwise old token isn't rejected
        // for a reason unrelated to revocation.
        Number tokenVersionClaim = claims.get("tv", Number.class);
        int tokenVersion = tokenVersionClaim == null ? 0 : tokenVersionClaim.intValue();
        return new AuthenticatedUser(claims.getSubject(), claims.get("email", String.class), roles, tokenVersion);
    }

    /** Opaque, high-entropy refresh token value returned to the client once. Never persisted raw. */
    public String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashOpaqueToken(String rawToken) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public long getRefreshExpirationSeconds() {
        return properties.refreshExpirationSeconds();
    }
}
