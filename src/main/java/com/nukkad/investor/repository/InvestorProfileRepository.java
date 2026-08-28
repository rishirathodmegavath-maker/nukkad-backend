package com.nukkad.investor.repository;

import com.nukkad.investor.entity.InvestorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface InvestorProfileRepository extends JpaRepository<InvestorProfile, String>, JpaSpecificationExecutor<InvestorProfile> {
    Optional<InvestorProfile> findByUserId(String userId);
    boolean existsByUserId(String userId);
}
