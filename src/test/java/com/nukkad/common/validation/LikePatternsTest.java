package com.nukkad.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternsTest {

    @Test
    void plainTextIsTrimmedLowerCasedAndWrappedInWildcards() {
        assertThat(LikePatterns.contains("  Health Tech ")).isEqualTo("%health tech%");
    }

    @Test
    void aPercentSignIsMadeLiteralSoSearchingForItDoesNotMatchEverything() {
        assertThat(LikePatterns.contains("%")).isEqualTo("%\\%%");
        assertThat(LikePatterns.contains("100%")).isEqualTo("%100\\%%");
    }

    @Test
    void anUnderscoreIsMadeLiteralSoItDoesNotMatchAnySingleCharacter() {
        assertThat(LikePatterns.contains("_")).isEqualTo("%\\_%");
        assertThat(LikePatterns.contains("a_b")).isEqualTo("%a\\_b%");
    }

    @Test
    void aBackslashIsEscapedFirstSoItCannotUndoTheOtherEscapes() {
        assertThat(LikePatterns.contains("\\")).isEqualTo("%\\\\%");
        assertThat(LikePatterns.contains("\\%")).isEqualTo("%\\\\\\%%");
    }

    @Test
    void lowerCasingIsLocaleIndependent() {
        // A Turkish default locale would turn "I" into a dotless i; the database side lower-cases with its own collation.
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(LikePatterns.contains("ICT")).isEqualTo("%ict%");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }
}
