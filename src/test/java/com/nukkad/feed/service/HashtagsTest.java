package com.nukkad.feed.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashtagsTest {

    @Test
    void findsTagsAnywhereInTheTextAndLowercasesThem() {
        assertThat(Hashtags.extract("Loving the #BuildInPublic movement and #AI")).containsExactly("buildinpublic", "ai");
        assertThat(Hashtags.extract("#startup at the start")).containsExactly("startup");
        assertThat(Hashtags.extract("multi\nline\n#tag_one\n")).containsExactly("tag_one");
    }

    @Test
    void eachTagCountsOnceInOrderOfFirstUse() {
        assertThat(Hashtags.extract("#bb #aa #BB #AA #cc")).containsExactly("bb", "aa", "cc");
    }

    @Test
    void punctuationAfterATagIsNotPartOfIt() {
        assertThat(Hashtags.extract("Big news #launch, #funding! (#india). #team?")).containsExactly("launch", "funding", "india", "team");
    }

    @Test
    void aTagInBracketsOrQuotesStillCounts() {
        assertThat(Hashtags.extract("(#ai) \"#ml\" [#saas]")).containsExactly("ai", "ml", "saas");
    }

    @Test
    void numbersAloneAreNotTags() {
        assertThat(Hashtags.extract("issue #12 and #2024 but #q3 and #2024plan")).containsExactly("q3", "2024plan");
    }

    @Test
    void aSingleCharacterIsNotATag() {
        assertThat(Hashtags.extract("#a and #1 and #ab")).containsExactly("ab");
    }

    @Test
    void aTagGluedToAWordOrAnotherHashIsNotATag() {
        assertThat(Hashtags.extract("abc#tag page#section ##heading #_ok")).containsExactly("_ok");
    }

    @Test
    void urlFragmentsAreNotTags() {
        assertThat(Hashtags.extract("see https://example.com/page#section and https://x.io/#/route")).isEmpty();
    }

    @Test
    void aTagLongerThanFiftyCharactersIsIgnoredNotTruncated() {
        String fifty = "a".repeat(50);
        assertThat(Hashtags.extract("#" + fifty + " #" + fifty + "b")).containsExactly(fifty);
    }

    @Test
    void lettersFromAnyLanguageWork() {
        assertThat(Hashtags.extract("#स्टार्टअप #नमस्ते #café")).containsExactly("स्टार्टअप", "नमस्ते", "café");
        assertThat(Hashtags.extract("#日本語 #naïve")).containsExactly("日本語", "naïve");
    }

    @Test
    void noMoreThanTenTagsPerPost() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 25; i++) text.append("#tag").append(i).append(' ');
        assertThat(Hashtags.extract(text.toString())).hasSize(Hashtags.MAX_TAGS_PER_POST);
    }

    @Test
    void emptyAndTaglessTextGivesNoTags() {
        assertThat(Hashtags.extract(null)).isEmpty();
        assertThat(Hashtags.extract("")).isEmpty();
        assertThat(Hashtags.extract("no tags, just a # sign and 100%")).isEmpty();
    }

    @Test
    void normalizeAcceptsATagWithOrWithoutTheHashAndAnyCase() {
        assertThat(Hashtags.normalize("#AI")).isEqualTo("ai");
        assertThat(Hashtags.normalize("  BuildInPublic ")).isEqualTo("buildinpublic");
    }

    @Test
    void normalizeRefusesAnythingThatCouldNeverBeStored() {
        assertThat(Hashtags.normalize(null)).isNull();
        assertThat(Hashtags.normalize("")).isNull();
        assertThat(Hashtags.normalize("#")).isNull();
        assertThat(Hashtags.normalize("a")).isNull();
        assertThat(Hashtags.normalize("12345")).isNull();
        assertThat(Hashtags.normalize("two words")).isNull();
        assertThat(Hashtags.normalize("bad!")).isNull();
        assertThat(Hashtags.normalize("a".repeat(51))).isNull();
    }
}
