package com.nukkad.report.repository;

import com.nukkad.report.entity.Report;
import com.nukkad.report.entity.ReportStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Objects;

public final class ReportSpecifications {

    private ReportSpecifications() {}

    @SafeVarargs
    public static Specification<Report> combine(Specification<Report>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<Report> status(ReportStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Report> category(String category) {
        if (category == null || category.isBlank()) return null;
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase());
    }

    public static Specification<Report> reportedUserId(String userId) {
        if (userId == null || userId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("reportedUserId"), userId);
    }
}
