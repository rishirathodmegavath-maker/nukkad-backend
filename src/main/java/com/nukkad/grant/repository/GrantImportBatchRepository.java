package com.nukkad.grant.repository;

import com.nukkad.grant.entity.GrantImportBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrantImportBatchRepository extends JpaRepository<GrantImportBatch, String> {

    Page<GrantImportBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
