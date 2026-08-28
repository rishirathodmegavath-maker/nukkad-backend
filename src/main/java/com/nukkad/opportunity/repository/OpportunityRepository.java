package com.nukkad.opportunity.repository;

import com.nukkad.opportunity.entity.Opportunity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OpportunityRepository extends JpaRepository<Opportunity, String>, JpaSpecificationExecutor<Opportunity> {
    Page<Opportunity> findByPostedByUserId(String postedByUserId, Pageable pageable);
    long countByChapterId(String chapterId);
}
