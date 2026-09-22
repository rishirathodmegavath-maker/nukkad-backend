package com.nukkad.investor.repository;

import com.nukkad.investor.entity.Investor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InvestorRepository extends JpaRepository<Investor, String>, JpaSpecificationExecutor<Investor> {
}
