package com.nukkad.wallet.service;

import com.nukkad.security.JwtProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The two cryptographic pieces of the wallet PIN, kept apart from the business rules.
 *
 * <p><b>Pepper.</b> A 6-digit PIN has only a million possibilities, so a leaked database of plain
 * BCrypt hashes could be cracked offline in minutes. The PIN is therefore first run through an
 * HMAC keyed with a secret that never touches the database, and only that result is BCrypted. A
 * database leak alone then reveals nothing about anyone's PIN.
 *
 * <p><b>Unlock token.</b> After a correct PIN the client gets a short-lived token that it sends on
 * every wallet request. It is deliberately <em>not</em> a JWT: the access-token filter accepts any
 * validly signed JWT as a login, and this token must never be usable as one. It is a plain
 * {@code base64url(payload).base64url(hmac)} pair. The payload binds it to one user, that user's
 * current session generation ({@code tokenVersion}) and PIN generation ({@code pinVersion}), plus an
 * expiry — so logging out everywhere, changing the password, changing or resetting the PIN, or a
 * lockout all invalidate it immediately.
 *
 * <p>Both keys are derived from the JWT signing secret with distinct labels. Rotating that secret
 * therefore also invalidates every PIN; members recover with "Forgot PIN" (account password), so no
 * data is lost.
 */
@Component
public class WalletPinCrypto {

    /** How long a correct PIN keeps the wallet open. */
    public static final long UNLOCK_TTL_SECONDS = 10 * 60;

    private static final String HMAC = "HmacSHA256";
    private static final String TOKEN_VERSION = "v1";

    private final byte[] pepperKey;
    private final byte[] unlockKey;
    private final Clock clock;

    @Autowired
    public WalletPinCrypto(JwtProperties jwtProperties) {
        this(jwtProperties.secret(), Clock.systemUTC());
    }

    WalletPinCrypto(String secret, Clock clock) {
        byte[] root = secret.getBytes(StandardCharsets.UTF_8);
        this.pepperKey = hmac(root, "wallet-pin-pepper-v1".getBytes(StandardCharsets.UTF_8));
        this.unlockKey = hmac(root, "wallet-unlock-token-v1".getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    /** What actually gets BCrypted: HMAC(pepper, userId:pin) as hex (64 chars, under BCrypt's 72-byte limit). */
    public String pepperedPin(String userId, String pin) {
        return HexFormat.of().formatHex(hmac(pepperKey, (userId + ":" + pin).getBytes(StandardCharsets.UTF_8)));
    }

    public String issueUnlockToken(String userId, int tokenVersion, int pinVersion) {
        long expiresAt = clock.instant().getEpochSecond() + UNLOCK_TTL_SECONDS;
        String payload = String.join(":", TOKEN_VERSION, userId, Integer.toString(tokenVersion),
                Integer.toString(pinVersion), Long.toString(expiresAt));
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return b64.encodeToString(payloadBytes) + "." + b64.encodeToString(hmac(unlockKey, payloadBytes));
    }

    /** True only for an unexpired token signed by us for exactly this user, session and PIN generation. Never throws. */
    public boolean isValidUnlockToken(String token, String userId, int tokenVersion, int pinVersion) {
        if (token == null || token.isBlank() || token.length() > 512) return false;
        try {
            int dot = token.indexOf('.');
            if (dot <= 0 || dot == token.length() - 1) return false;
            Base64.Decoder b64 = Base64.getUrlDecoder();
            byte[] payloadBytes = b64.decode(token.substring(0, dot));
            byte[] signature = b64.decode(token.substring(dot + 1));
            // Constant-time comparison: the signature is checked before a single payload field is trusted.
            if (!MessageDigest.isEqual(hmac(unlockKey, payloadBytes), signature)) return false;

            String[] parts = new String(payloadBytes, StandardCharsets.UTF_8).split(":", -1);
            if (parts.length != 5 || !TOKEN_VERSION.equals(parts[0])) return false;
            return parts[1].equals(userId)
                    && Integer.parseInt(parts[2]) == tokenVersion
                    && Integer.parseInt(parts[3]) == pinVersion
                    && Long.parseLong(parts[4]) > clock.instant().getEpochSecond();
        } catch (IllegalArgumentException e) {
            // Bad base64 or a non-numeric field: not a token we issued.
            return false;
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
