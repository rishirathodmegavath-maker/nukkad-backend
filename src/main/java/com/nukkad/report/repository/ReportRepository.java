package com.nukkad.report.repository;

import com.nukkad.report.entity.Report;
import com.nukkad.report.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportRepository extends JpaRepository<Report, String>, JpaSpecificationExecutor<Report> {
    long countByStatus(ReportStatus status);
}
