package com.nukkad.investor.repository;

import com.nukkad.investor.entity.Fundraise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface FundraiseRepository extends JpaRepository<Fundraise, String>, JpaSpecificationExecutor<Fundraise> {
    Optional<Fundraise> findByStartupId(String startupId);
    boolean existsByStartupId(String startupId);
}
