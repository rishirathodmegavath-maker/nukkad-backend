package com.nukkad.opportunity.repository;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.opportunity.entity.Opportunity;
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
 * These specifications are the actual DB-level gate that keeps a PENDING or REJECTED opportunity out of public
 * discovery (see OpportunityService#listOpportunities). No database is needed to prove they build the right
 * predicate: a {@link Specification#toPredicate} is a plain method that only calls into the mocked
 * CriteriaBuilder, so a bug like comparing against the wrong ModerationStatus constant is caught here without
 * Testcontainers/Docker.
 */
class OpportunitySpecificationsTest {

    @SuppressWarnings("unchecked")
    private final Root<Opportunity> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    private final Path<Object> moderationStatusPath = mock(Path.class);

    OpportunitySpecificationsTest() {
        when(root.<Object>get("moderationStatus")).thenReturn(moderationStatusPath);
    }

    @Test
    void approvedBuildsAnEqualityCheckAgainstApprovedNotSomeOtherStatus() {
        OpportunitySpecifications.approved().toPredicate(root, query, cb);

        verify(cb).equal(moderationStatusPath, ModerationStatus.APPROVED);
        verify(cb, never()).equal(moderationStatusPath, ModerationStatus.PENDING);
        verify(cb, never()).equal(moderationStatusPath, ModerationStatus.REJECTED);
    }

    @Test
    void moderationStatusFilterMatchesExactlyTheRequestedStatus() {
        OpportunitySpecifications.moderationStatus(ModerationStatus.PENDING).toPredicate(root, query, cb);

        verify(cb).equal(moderationStatusPath, ModerationStatus.PENDING);
        verify(cb, never()).equal(moderationStatusPath, ModerationStatus.APPROVED);
    }

    @Test
    void moderationStatusFilterIsOmittedEntirelyWhenNoStatusIsRequested() {
        // The admin queue's "all statuses" view: this must not silently narrow to one status.
        assertThat(OpportunitySpecifications.moderationStatus(null)).isNull();
    }

    @Test
    void combineSkipsNullFiltersInsteadOfThrowing() {
        Predicate sentinel = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(sentinel);

        Specification<Opportunity> combined = OpportunitySpecifications.combine(null, null);

        assertThat(combined.toPredicate(root, query, cb)).isSameAs(sentinel);
    }

    @Test
    void combiningApprovedWithAnotherFilterStillAppliesBothOnEvaluation() {
        Predicate approvedPredicate = mock(Predicate.class);
        Predicate openPredicate = mock(Predicate.class);
        Path<Boolean> closedPath = mockBooleanPath();
        when(root.<Boolean>get("closed")).thenReturn(closedPath);
        when(cb.equal(moderationStatusPath, ModerationStatus.APPROVED)).thenReturn(approvedPredicate);
        when(cb.isFalse(closedPath)).thenReturn(openPredicate);

        Specification<Opportunity> combined = OpportunitySpecifications.combine(
                OpportunitySpecifications.approved(), OpportunitySpecifications.open());
        combined.toPredicate(root, query, cb);

        // Evaluating the combined specification must still touch both leaf checks — proving a real
        // discovery query (open() + approved() together) can't end up only checking one of the two.
        verify(cb).equal(moderationStatusPath, ModerationStatus.APPROVED);
        verify(cb).isFalse(closedPath);
    }

    @SuppressWarnings("unchecked")
    private static Path<Boolean> mockBooleanPath() {
        return mock(Path.class);
    }
}
