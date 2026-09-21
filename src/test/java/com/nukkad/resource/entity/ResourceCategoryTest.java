package com.nukkad.resource.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceCategoryTest {

    @Test
    void everyShelfRoundTripsThroughItsSlug() {
        for (ResourceCategory category : ResourceCategory.values()) {
            assertThat(ResourceCategory.fromSlug(category.getSlug())).isSameAs(category);
        }
    }

    @Test
    void slugLookupIgnoresCase() {
        assertThat(ResourceCategory.fromSlug("Startup-Blocks")).isSameAs(ResourceCategory.STARTUP_BLOCKS);
    }

    @Test
    void startupEssaysKeepTheOriginalPlaybooksSlugSoExistingRowsStillLoad() {
        // "Startup Essays" is only the display label in the front end; changing the stored slug would make
        // every row already filed on this shelf fail to load.
        assertThat(ResourceCategory.fromSlug("playbooks")).isSameAs(ResourceCategory.PLAYBOOKS);
    }

    @Test
    void newShelfUsesAStableSlugThatFitsTheColumn() {
        assertThat(ResourceCategory.STARTUP_BLOCKS.getSlug()).isEqualTo("startup-blocks");
        // resources.category is VARCHAR(30) (V82).
        for (ResourceCategory category : ResourceCategory.values()) {
            assertThat(category.getSlug().length()).isLessThanOrEqualTo(30);
        }
    }

    @Test
    void unknownSlugIsRejected() {
        assertThatThrownBy(() -> ResourceCategory.fromSlug("nope")).isInstanceOf(IllegalArgumentException.class);
    }
}
