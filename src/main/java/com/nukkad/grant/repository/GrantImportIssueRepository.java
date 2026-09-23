package com.nukkad.grant.repository;

import com.nukkad.grant.entity.GrantImportIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrantImportIssueRepository extends JpaRepository<GrantImportIssue, String> {

    Page<GrantImportIssue> findByBatchIdOrderByRowNumberAsc(String batchId, Pageable pageable);
}
