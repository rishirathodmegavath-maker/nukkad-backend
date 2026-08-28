package com.nukkad.opportunity.repository;

import com.nukkad.opportunity.entity.ApplicationStatus;
import com.nukkad.opportunity.entity.OpportunityApplicant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OpportunityApplicantRepository extends JpaRepository<OpportunityApplicant, String> {
    boolean existsByOpportunityIdAndUserId(String opportunityId, String userId);
    Optional<OpportunityApplicant> findByOpportunityIdAndUserId(String opportunityId, String userId);
    List<OpportunityApplicant> findByUserId(String userId);
    long countByOpportunityId(String opportunityId);

    Page<OpportunityApplicant> findByOpportunityIdOrderByCreatedAtDesc(String opportunityId, Pageable pageable);
    Page<OpportunityApplicant> findByOpportunityIdAndStatusOrderByCreatedAtDesc(String opportunityId, ApplicationStatus status, Pageable pageable);

    /** Whether an ACCEPTED application exists between these two users, in either applicant/poster direction. */
    @Query("select case when count(a) > 0 then true else false end from OpportunityApplicant a "
            + "join Opportunity o on o.id = a.opportunityId "
            + "where a.status = com.nukkad.opportunity.entity.ApplicationStatus.ACCEPTED "
            + "and ((a.userId = :userA and o.postedByUserId = :userB) or (a.userId = :userB and o.postedByUserId = :userA))")
    boolean existsAcceptedApplicationBetween(@Param("userA") String userA, @Param("userB") String userB);
}
