package com.nukkad.wallet.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WalletPinCryptoTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123456789";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private final WalletPinCrypto crypto = new WalletPinCrypto(SECRET, at(NOW));

    @Test
    void aFreshTokenIsValidForExactlyTheUserAndGenerationsItWasIssuedFor() {
        String token = crypto.issueUnlockToken("user-1", 3, 7);
        assertThat(crypto.isValidUnlockToken(token, "user-1", 3, 7)).isTrue();
    }

    @Test
    void aTokenIsRefusedForAnotherUser() {
        String token = crypto.issueUnlockToken("user-1", 3, 7);
        assertThat(crypto.isValidUnlockToken(token, "user-2", 3, 7)).isFalse();
    }

    @Test
    void aTokenDiesWhenTheSessionGenerationChanges() {
        // tokenVersion bumps on logout-everywhere / password change.
        String token = crypto.issueUnlockToken("user-1", 3, 7);
        assertThat(crypto.isValidUnlockToken(token, "user-1", 4, 7)).isFalse();
    }

    @Test
    void aTokenDiesWhenThePinChangesOrTheWalletLocks() {
        String token = crypto.issueUnlockToken("user-1", 3, 7);
        assertThat(crypto.isValidUnlockToken(token, "user-1", 3, 8)).isFalse();
    }

    @Test
    void aTokenExpiresAfterTheTtl() {
        String token = crypto.issueUnlockToken("user-1", 3, 7);
        Instant justBefore = NOW.plusSeconds(WalletPinCrypto.UNLOCK_TTL_SECONDS - 1);
        Instant expired = NOW.plusSeconds(WalletPinCrypto.UNLOCK_TTL_SECONDS);
        assertThat(new WalletPinCrypto(SECRET, at(justBefore)).isValidUnlockToken(token, "user-1", 3, 7)).isTrue();
        assertThat(new WalletPinCrypto(SECRET, at(expired)).isValidUnlockToken(token, "user-1", 3, 7)).isFalse();
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRefused() {
        String forged = new WalletPinCrypto("another-secret-another-secret-another-0000", at(NOW))
                .issueUnlockToken("user-1", 3, 7);
        assertThat(crypto.isValidUnlockToken(forged, "user-1", 3, 7)).isFalse();
    }

    @Test
    void aTamperedPayloadIsRefusedEvenWithTheOriginalSignature() {
        String token = crypto.issueUnlockToken("user-1", 3, 7);
        String signature = token.substring(token.indexOf('.') + 1);
        // Same signature, but the payload now claims a much later expiry.
        String payload = "v1:user-1:3:7:" + (NOW.getEpochSecond() + Duration.ofDays(365).toSeconds());
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "." + signature;
        assertThat(crypto.isValidUnlockToken(tampered, "user-1", 3, 7)).isFalse();
    }

    @Test
    void garbageNeverThrowsAndIsRefused() {
        for (String junk : new String[] {null, "", " ", ".", "abc", "abc.", ".abc", "a.b.c", "!!!.???", "x".repeat(600)}) {
            assertThat(crypto.isValidUnlockToken(junk, "user-1", 3, 7)).as("junk: %s", junk).isFalse();
        }
    }

    @Test
    void aTokenIsNotAJwt() {
        // The access-token filter treats any validly signed JWT as a login. The unlock token must
        // not look like one (three dot-separated parts) so it can never be mistaken for one.
        String token = crypto.issueUnlockToken("user-1", 3, 7);
        assertThat(token.split("\\.", -1)).hasSize(2);
    }

    @Test
    void thePepperedPinIsStableAndSpecificToTheUserAndPin() {
        String a = crypto.pepperedPin("user-1", "482913");
        assertThat(a).isEqualTo(crypto.pepperedPin("user-1", "482913")).hasSize(64);
        assertThat(a).isNotEqualTo(crypto.pepperedPin("user-2", "482913"));
        assertThat(a).isNotEqualTo(crypto.pepperedPin("user-1", "482914"));
        assertThat(a).doesNotContain("482913");
    }

    @Test
    void thePepperDependsOnTheServerSecret() {
        String other = new WalletPinCrypto("another-secret-another-secret-another-0000", at(NOW))
                .pepperedPin("user-1", "482913");
        assertThat(other).isNotEqualTo(crypto.pepperedPin("user-1", "482913"));
    }
}
