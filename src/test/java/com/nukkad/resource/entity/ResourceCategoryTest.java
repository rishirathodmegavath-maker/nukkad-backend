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
        assertThat(ResourceCategory.fromSlug("Startup-Blocks")).isSameAs(ResourceCategory.STARTUP_VLOGS);
    }

    @Test
    void renamedShelvesKeepTheirOriginalSlugsSoExistingRowsStillLoad() {
        // "Startup Essays", "Pitch Deck" and "Startup Vlogs" are only display labels in the front end;
        // changing a stored slug would make every row already filed on that shelf fail to load.
        assertThat(ResourceCategory.fromSlug("playbooks")).isSameAs(ResourceCategory.PLAYBOOKS);
        assertThat(ResourceCategory.fromSlug("templates")).isSameAs(ResourceCategory.TEMPLATES);
        assertThat(ResourceCategory.fromSlug("startup-blocks")).isSameAs(ResourceCategory.STARTUP_VLOGS);
    }

    @Test
    void newShelfUsesAStableSlugThatFitsTheColumn() {
        assertThat(ResourceCategory.VIDEOS.getSlug()).isEqualTo("videos");
        assertThat(ResourceCategory.fromSlug("videos")).isSameAs(ResourceCategory.VIDEOS);
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
