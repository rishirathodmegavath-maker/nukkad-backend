package com.nukkad.investor.repository;

import com.nukkad.investor.entity.InvestorImportBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestorImportBatchRepository extends JpaRepository<InvestorImportBatch, String> {

    Page<InvestorImportBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
