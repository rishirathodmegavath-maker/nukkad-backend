package com.nukkad.feed.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The backfill (V110) and the admin create-post flow both hardcode this exact six-identity list
 *  elsewhere (SQL literals, AdminPostFormModal's dropdown) — this pins the enum itself so a future
 *  edit to it doesn't silently desync from either. */
class PostPublisherIdentityEnumTest {

    @Test
    void hasExactlyTheSixCuratedIdentitiesInDeclarationOrder() {
        List<String> names = Arrays.stream(Post.PublisherIdentity.values())
                .map(Enum::name)
                .toList();

        assertThat(names).containsExactly(
                "BUILDADDA", "BUILDADDA_INSIGHTS", "BUILDADDA_GRANTS",
                "BUILDADDA_COMMUNITY", "BUILDADDA_STARTUP_DESK", "BUILDADDA_EDITORIAL");
    }

    @Test
    void everyIdentityHasAHumanReadableLabel() {
        for (Post.PublisherIdentity identity : Post.PublisherIdentity.values()) {
            assertThat(identity.getLabel()).as("label for %s", identity).startsWith("BuildAdda");
        }
    }
}
