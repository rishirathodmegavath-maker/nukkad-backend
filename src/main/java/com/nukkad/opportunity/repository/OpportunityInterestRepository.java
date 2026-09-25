package com.nukkad.opportunity.repository;

import com.nukkad.opportunity.entity.OpportunityInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OpportunityInterestRepository extends JpaRepository<OpportunityInterest, String> {
    boolean existsByOpportunityIdAndUserId(String opportunityId, String userId);
    List<OpportunityInterest> findByUserId(String userId);
    long countByOpportunityId(String opportunityId);

    /** Which opportunities (of a page's worth) this viewer has already expressed interest in, in one query
     *  instead of one {@link #existsByOpportunityIdAndUserId} call per row. */
    List<OpportunityInterest> findByOpportunityIdInAndUserId(List<String> opportunityIds, String userId);

    /** Interest counts for a whole page of opportunities in one query, instead of one
     *  {@link #countByOpportunityId} call per row. */
    @Query("select i.opportunityId as opportunityId, count(i) as total from OpportunityInterest i "
            + "where i.opportunityId in :opportunityIds group by i.opportunityId")
    List<OpportunityIdCount> countGroupedByOpportunityIdIn(@Param("opportunityIds") List<String> opportunityIds);
}
