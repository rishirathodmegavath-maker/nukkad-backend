package com.nukkad.opportunity.repository;

import com.nukkad.opportunity.entity.OpportunityInterest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpportunityInterestRepository extends JpaRepository<OpportunityInterest, String> {
    boolean existsByOpportunityIdAndUserId(String opportunityId, String userId);
    List<OpportunityInterest> findByUserId(String userId);
    long countByOpportunityId(String opportunityId);
}
