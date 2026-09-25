package com.nukkad.idea.repository;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.idea.entity.Idea;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * These specifications are the actual DB-level gate that keeps a PENDING or REJECTED idea out of public
 * discovery (see IdeaService#listIdeas). No database is needed to prove they build the right predicate — see
 * OpportunitySpecificationsTest for the identical reasoning on the sibling moderation gate.
 */
class IdeaSpecificationsTest {

    @SuppressWarnings("unchecked")
    private final Root<Idea> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    private final Path<Object> moderationStatusPath = mock(Path.class);

    IdeaSpecificationsTest() {
        when(root.<Object>get("moderationStatus")).thenReturn(moderationStatusPath);
    }

    @Test
    void approvedBuildsAnEqualityCheckAgainstApprovedNotSomeOtherStatus() {
        IdeaSpecifications.approved().toPredicate(root, query, cb);

        verify(cb).equal(moderationStatusPath, ModerationStatus.APPROVED);
        verify(cb, never()).equal(moderationStatusPath, ModerationStatus.PENDING);
        verify(cb, never()).equal(moderationStatusPath, ModerationStatus.REJECTED);
    }

    @Test
    void moderationStatusFilterIsOmittedEntirelyWhenNoStatusIsRequested() {
        assertThat(IdeaSpecifications.moderationStatus(null)).isNull();
    }

    @Test
    void combineSkipsNullFiltersInsteadOfThrowing() {
        Predicate sentinel = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(sentinel);

        Specification<Idea> combined = IdeaSpecifications.combine(null, null);

        assertThat(combined.toPredicate(root, query, cb)).isSameAs(sentinel);
    }
}
