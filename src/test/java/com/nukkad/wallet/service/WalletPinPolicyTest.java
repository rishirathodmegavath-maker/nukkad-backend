package com.nukkad.wallet.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class WalletPinPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"000000", "111111", "999999", "123456", "234567", "345678", "456789",
            "654321", "987654", "876543", "121212", "090909", "123123", "789789"})
    void rejectsTheEasiestGuesses(String pin) {
        assertThat(WalletPinPolicy.isWeak(pin)).as(pin).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"482913", "135790", "718293", "100200", "120345", "246810", "908172"})
    void acceptsOrdinaryPins(String pin) {
        assertThat(WalletPinPolicy.isWeak(pin)).as(pin).isFalse();
    }

    @Test
    void anythingNotSixCharactersCountsAsWeak() {
        assertThat(WalletPinPolicy.isWeak(null)).isTrue();
        assertThat(WalletPinPolicy.isWeak("48291")).isTrue();
        assertThat(WalletPinPolicy.isWeak("4829135")).isTrue();
    }
}
