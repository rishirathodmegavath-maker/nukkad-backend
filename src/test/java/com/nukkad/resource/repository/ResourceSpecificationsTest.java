package com.nukkad.resource.repository;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.resource.entity.ResourceCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceSpecificationsTest {

    @Test
    void anUnknownCategoryFilterIsABadRequestNotAnEmptyResult() {
        assertThatThrownBy(() -> ResourceSpecifications.category("not-a-shelf")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aBlankOrMissingCategoryFilterMeansNoRestriction() {
        assertThat(ResourceSpecifications.category(null)).isNull();
        assertThat(ResourceSpecifications.category("  ")).isNull();
    }

    @Test
    void featuredOnlyRestrictsWhenTrue() {
        assertThat(ResourceSpecifications.featured(null)).isNull();
        assertThat(ResourceSpecifications.featured(false)).isNull();
        assertThat(ResourceSpecifications.featured(true)).isNotNull();
    }

    @Test
    void everyCategoryRoundTripsThroughItsSlug() {
        for (ResourceCategory c : ResourceCategory.values()) {
            assertThat(ResourceCategory.fromSlug(c.getSlug())).isEqualTo(c);
        }
        assertThat(ResourceCategory.fromSlug("FREE-LEARNING")).isEqualTo(ResourceCategory.FREE_LEARNING);
    }
}
