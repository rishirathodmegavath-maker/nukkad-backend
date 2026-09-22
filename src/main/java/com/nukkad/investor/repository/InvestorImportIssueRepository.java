package com.nukkad.investor.repository;

import com.nukkad.investor.entity.InvestorImportIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestorImportIssueRepository extends JpaRepository<InvestorImportIssue, String> {

    Page<InvestorImportIssue> findByBatchIdOrderByRowNumberAsc(String batchId, Pageable pageable);
}
