package com.nukkad.investor.repository;

import com.nukkad.investor.entity.Investor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface InvestorRepository extends JpaRepository<Investor, String>, JpaSpecificationExecutor<Investor> {

    /** Drives the CSV import's upsert: a row whose "id" column matches an existing investor's
     *  {@code externalSourceId} is updated in place instead of creating a duplicate. */
    Optional<Investor> findByExternalSourceId(String externalSourceId);
}
