package com.nukkad.report.repository;

import com.nukkad.report.entity.Report;
import com.nukkad.report.entity.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, String>, JpaSpecificationExecutor<Report> {
    long countByStatus(ReportStatus status);

    // Row-locked read for resolve(): without this, two concurrent resolve calls on the same OPEN
    // report can both read status == OPEN before either commits, so both succeed instead of one
    // winning and the other correctly hitting the "already reviewed" conflict.
    // Explicit @Query, not name-derivation: "ForUpdate" isn't a Spring Data keyword -- a plain
    // `findByIdForUpdate(String)` with only @Lock fails at startup the same way documented on
    // RefreshTokenRepository#findByTokenHashForUpdate. Mirrors WalletRepository#findByIdForUpdate.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") String id);
}
