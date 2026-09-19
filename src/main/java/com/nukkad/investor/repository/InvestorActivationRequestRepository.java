package com.nukkad.investor.repository;

import com.nukkad.investor.entity.InvestorActivationRequest;
import com.nukkad.investor.entity.InvestorActivationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvestorActivationRequestRepository extends JpaRepository<InvestorActivationRequest, String> {

    Optional<InvestorActivationRequest> findTopByRequesterUserIdOrderByCreatedAtDesc(String requesterUserId);

    Page<InvestorActivationRequest> findByStatusOrderByCreatedAtDesc(InvestorActivationStatus status, Pageable pageable);

    Page<InvestorActivationRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(InvestorActivationStatus status);
}
