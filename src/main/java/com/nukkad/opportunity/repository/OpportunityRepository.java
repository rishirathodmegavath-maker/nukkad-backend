package com.nukkad.opportunity.repository;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.opportunity.entity.Opportunity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OpportunityRepository extends JpaRepository<Opportunity, String>, JpaSpecificationExecutor<Opportunity> {
    Page<Opportunity> findByPostedByUserId(String postedByUserId, Pageable pageable);
    long countByChapterId(String chapterId);
    long countByClosedFalse();
    long countByStartupId(String startupId);
    long countByModerationStatus(ModerationStatus moderationStatus);

    /** Feeds the chapter "recent activity" list — pageable's Sort (createdAt desc) determines order. */
    java.util.List<Opportunity> findByChapterId(String chapterId, Pageable pageable);
}
