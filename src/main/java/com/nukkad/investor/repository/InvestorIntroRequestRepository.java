package com.nukkad.investor.repository;

import com.nukkad.investor.entity.InvestorIntroRequest;
import com.nukkad.investor.entity.InvestorIntroRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestorIntroRequestRepository extends JpaRepository<InvestorIntroRequest, String> {
    boolean existsByInvestorIdAndStartupIdAndStatus(String investorId, String startupId, InvestorIntroRequestStatus status);
    Page<InvestorIntroRequest> findByStatusOrderByCreatedAtDesc(InvestorIntroRequestStatus status, Pageable pageable);
    Page<InvestorIntroRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
