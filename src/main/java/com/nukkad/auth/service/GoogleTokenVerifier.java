package com.nukkad.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.nukkad.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Verifies Google "Sign In With Google" identities server-side, via either of two entry points:
 * an ID token handed directly to the frontend (no client secret involved), or an OAuth
 * authorization code from the redirect-based flow (exchanged here using the client secret,
 * which never reaches the frontend). Both paths converge on the same {@link #verify} check.
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;
    private final boolean configured;
    private final String clientId;
    private final String clientSecret;

    public GoogleTokenVerifier(@Value("${nukkad.google.client-id:}") String clientId,
                                @Value("${nukkad.google.client-secret:}") String clientSecret) {
        this.configured = clientId != null && !clientId.isBlank();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.verifier = configured
                ? new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(clientId))
                        .build()
                : null;
    }

    public record GoogleIdentity(String email, String name, String pictureUrl) {}

    public GoogleIdentity verify(String rawIdToken) {
        if (!configured) {
            throw new UnauthorizedException("Google Sign-In is not configured on this server");
        }
        try {
            GoogleIdToken idToken = verifier.verify(rawIdToken);
            if (idToken == null) {
                throw new UnauthorizedException("Invalid or expired Google token");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new UnauthorizedException("Google account email is not verified");
            }
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");
            return new GoogleIdentity(payload.getEmail(), name, picture);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new UnauthorizedException("Could not verify Google token");
        }
    }

    /**
     * Exchanges an OAuth authorization code (from the redirect-based sign-in flow) for Google's
     * own ID token, server-side, using the client secret — then runs it through the exact same
     * {@link #verify} check used by the direct ID-token flow. The redirect URI must exactly match
     * the one used to obtain the code, per Google's OAuth requirements.
     */
    public GoogleIdentity exchangeAuthorizationCode(String code, String redirectUri) {
        if (!configured || clientSecret == null || clientSecret.isBlank()) {
            throw new UnauthorizedException("Google Sign-In is not configured on this server");
        }
        try {
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientId,
                    clientSecret,
                    code,
                    redirectUri)
                    .execute();
            String idToken = tokenResponse.getIdToken();
            if (idToken == null || idToken.isBlank()) {
                throw new UnauthorizedException("Google did not return an identity token");
            }
            return verify(idToken);
        } catch (IOException e) {
            throw new UnauthorizedException("Could not exchange Google authorization code");
        }
    }
}
