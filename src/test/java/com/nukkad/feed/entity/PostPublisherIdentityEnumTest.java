package com.nukkad.feed.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The backfill (V112) and the admin create-post flow both hardcode this exact four-identity list
 *  elsewhere (SQL literals, AdminPostFormModal's dropdown) — this pins the enum itself so a future
 *  edit to it doesn't silently desync from either. */
class PostPublisherIdentityEnumTest {

    @Test
    void hasExactlyTheFourAuthorizedIdentitiesInDeclarationOrder() {
        List<String> names = Arrays.stream(Post.PublisherIdentity.values())
                .map(Enum::name)
                .toList();

        assertThat(names).containsExactly("ARJUN_MEHTA", "KARAN_SHAH", "NEEL_KAPOOR", "VIKRAM_RAO");
    }

    @Test
    void everyIdentityHasItsFullDisplayName() {
        assertThat(Post.PublisherIdentity.ARJUN_MEHTA.getLabel()).isEqualTo("Arjun Mehta");
        assertThat(Post.PublisherIdentity.KARAN_SHAH.getLabel()).isEqualTo("Karan Shah");
        assertThat(Post.PublisherIdentity.NEEL_KAPOOR.getLabel()).isEqualTo("Neel Kapoor");
        assertThat(Post.PublisherIdentity.VIKRAM_RAO.getLabel()).isEqualTo("Vikram Rao");
    }
}
