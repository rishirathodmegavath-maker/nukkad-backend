package com.nukkad.program.repository;

import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ProgramApplicationRepository extends JpaRepository<ProgramApplication, String>, JpaSpecificationExecutor<ProgramApplication> {

    Optional<ProgramApplication> findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc(String applicantUserId, Program program);

    /** One row per program for a user's "My Applications" dashboard — the service picks the latest
     *  per program out of this, oldest-first so a later one naturally overwrites an earlier one in
     *  a per-program map. */
    List<ProgramApplication> findByApplicantUserIdOrderByCreatedAtAsc(String applicantUserId);
}
