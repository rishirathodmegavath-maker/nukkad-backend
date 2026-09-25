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
    List<OpportunityApplicant> findByOpportunityId(String opportunityId);
    long countByOpportunityId(String opportunityId);
    long countByOpportunityIdAndStatusNotIn(String opportunityId, List<ApplicationStatus> excludedStatuses);

    Page<OpportunityApplicant> findByOpportunityIdOrderByCreatedAtDesc(String opportunityId, Pageable pageable);
    Page<OpportunityApplicant> findByOpportunityIdAndStatusOrderByCreatedAtDesc(String opportunityId, ApplicationStatus status, Pageable pageable);

    /** One viewer's application (if any) across a whole page of opportunities, in a single query — the
     *  batched replacement for calling {@link #findByOpportunityIdAndUserId} once per row. */
    List<OpportunityApplicant> findByOpportunityIdInAndUserId(List<String> opportunityIds, String userId);

    /** Applicant counts (excluding withdrawn/rejected) for a whole page of opportunities in one query,
     *  instead of one {@link #countByOpportunityIdAndStatusNotIn} call per row. */
    @Query("select a.opportunityId as opportunityId, count(a) as total from OpportunityApplicant a "
            + "where a.opportunityId in :opportunityIds and a.status not in :excludedStatuses "
            + "group by a.opportunityId")
    List<OpportunityIdCount> countGroupedByOpportunityIdInAndStatusNotIn(@Param("opportunityIds") List<String> opportunityIds,
                                                                          @Param("excludedStatuses") List<ApplicationStatus> excludedStatuses);

    /** Whether an ACCEPTED application exists between these two users, in either applicant/poster direction. */
    @Query("select case when count(a) > 0 then true else false end from OpportunityApplicant a "
            + "join Opportunity o on o.id = a.opportunityId "
            + "where a.status = com.nukkad.opportunity.entity.ApplicationStatus.ACCEPTED "
            + "and ((a.userId = :userA and o.postedByUserId = :userB) or (a.userId = :userB and o.postedByUserId = :userA))")
    boolean existsAcceptedApplicationBetween(@Param("userA") String userA, @Param("userB") String userB);

    /** Applications received (not counting ones the applicant withdrew) on the opportunities attributed to a startup. */
    @Query("select count(a) from OpportunityApplicant a join Opportunity o on o.id = a.opportunityId "
            + "where o.startupId = :startupId and a.status <> com.nukkad.opportunity.entity.ApplicationStatus.WITHDRAWN")
    long countByStartupId(@Param("startupId") String startupId);

    /** Total applications received across every opportunity this user has posted — for the Founder Dashboard. */
    @Query("select count(a) from OpportunityApplicant a join Opportunity o on o.id = a.opportunityId "
            + "where o.postedByUserId = :userId")
    long countByPostedByUserId(@Param("userId") String userId);
}
