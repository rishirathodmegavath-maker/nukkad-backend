package com.nukkad.resource.repository;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.resource.entity.Resource;
import com.nukkad.resource.entity.ResourceCategory;
import com.nukkad.resource.entity.ResourceType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void anUnknownTypeFilterIsABadRequestNotARawIllegalArgument() {
        assertThatThrownBy(() -> ResourceSpecifications.type("Podcast")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aBlankOrMissingSearchTermMeansNoRestriction() {
        assertThat(ResourceSpecifications.search(null)).isNull();
        assertThat(ResourceSpecifications.search("  ")).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchingAnExactTypeWordAlsoMatchesThatTypeEvenWithoutTheWordInTheText() {
        Root<Resource> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> typePath = mock(Path.class);
        when(root.<Object>get("type")).thenReturn(typePath);

        ResourceSpecifications.search("video").toPredicate(root, query, cb);
        ResourceSpecifications.search("Videos").toPredicate(root, query, cb);
        ResourceSpecifications.search("deck").toPredicate(root, query, cb);

        verify(cb, times(2)).equal(typePath, ResourceType.VIDEO);
        verify(cb).equal(typePath, ResourceType.DECK);
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchingAnOrdinaryWordNeverBecomesATypeFilter() {
        Root<Resource> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        ResourceSpecifications.search("startup").toPredicate(root, query, cb);
        // A word this ordinary is never treated as a type filter — only an exact type-label match is.
        verify(root, never()).get("type");
    }
}
