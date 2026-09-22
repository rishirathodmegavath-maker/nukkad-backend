package com.nukkad.investor.repository;

import com.nukkad.investor.entity.Investor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InvestorRepository extends JpaRepository<Investor, String>, JpaSpecificationExecutor<Investor> {

    /** Drives the CSV import's upsert: a row whose "id" column matches an existing investor's
     *  {@code externalSourceId} is updated in place instead of creating a duplicate. */
    Optional<Investor> findByExternalSourceId(String externalSourceId);

    /** Every distinct sector value across the investors a founder can actually filter to (active + visible) —
     *  sector has no fixed enum, so this is the real vocabulary, not a guessed-at list. Restricted to
     *  publicly-visible rows so a facet never points at a filter combination that returns nothing. */
    @Query("select distinct s from Investor i join i.sectors s where i.active = true and i.visible = true")
    List<String> findDistinctVisibleSectors();

    /** Same as {@link #findDistinctVisibleSectors()}, for stage. */
    @Query("select distinct s from Investor i join i.stages s where i.active = true and i.visible = true")
    List<String> findDistinctVisibleStages();
}
