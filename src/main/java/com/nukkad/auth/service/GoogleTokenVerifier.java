package com.nukkad.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.nukkad.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/** Verifies Google "Sign In With Google" ID tokens server-side. No client secret involved. */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;
    private final boolean configured;

    public GoogleTokenVerifier(@Value("${nukkad.google.client-id:}") String clientId) {
        this.configured = clientId != null && !clientId.isBlank();
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
}
