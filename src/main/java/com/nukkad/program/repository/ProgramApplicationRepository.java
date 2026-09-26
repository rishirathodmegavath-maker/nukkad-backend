package com.nukkad.program.repository;

import com.nukkad.program.entity.ProgramApplication;
import com.nukkad.program.entity.ProgramApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProgramApplicationRepository extends JpaRepository<ProgramApplication, String>, JpaSpecificationExecutor<ProgramApplication> {

    Optional<ProgramApplication> findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc(String applicantUserId, String program);

    /** One row per program for a user's "My Applications" dashboard — the service picks the latest
     *  per program out of this, oldest-first so a later one naturally overwrites an earlier one in
     *  a per-program map. */
    List<ProgramApplication> findByApplicantUserIdOrderByCreatedAtAsc(String applicantUserId);

    /** How many people have actually applied — a draft nobody has submitted yet isn't a real
     *  application, so it's excluded from the admin list's "Applications" count. */
    long countByProgramAndStatusNot(String program, ProgramApplicationStatus excludedStatus);

    /** Keeps every existing application pointed at the right program when Admin renames its slug
     *  (see {@code AdminProgramService#update}) — a bulk update rather than fetch-modify-save-per-row,
     *  since a well-established program could have thousands of rows. */
    @Modifying
    @Query("UPDATE ProgramApplication p SET p.program = :newSlug WHERE p.program = :oldSlug")
    int renameProgramSlug(@Param("oldSlug") String oldSlug, @Param("newSlug") String newSlug);
}
