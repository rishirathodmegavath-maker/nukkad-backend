package com.nukkad.investor.repository;

import com.nukkad.common.validation.LikePatterns;

import com.nukkad.investor.entity.Investor;
import com.nukkad.investor.entity.InvestorType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Objects;

public final class InvestorSpecifications {

    private InvestorSpecifications() {}

    @SafeVarargs
    public static Specification<Investor> combine(Specification<Investor>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    /** Founder-facing Discovery only ever sees active + visible rows — applied unconditionally there so a hidden
     *  or deactivated investor cannot surface through search/filter combinations (never applied for admin lists). */
    public static Specification<Investor> publiclyVisible() {
        return (root, query, cb) -> cb.and(cb.isTrue(root.get("active")), cb.isTrue(root.get("visible")));
    }

    public static Specification<Investor> search(String q) {
        if (q == null || q.isBlank()) return null;
        String like = LikePatterns.contains(q);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("location"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("country"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("domain"), "")), like)
        );
    }

    public static Specification<Investor> type(String typeLabel) {
        if (typeLabel == null || typeLabel.isBlank()) return null;
        InvestorType type = InvestorType.fromLabel(typeLabel.trim());
        return (root, query, cb) -> cb.equal(root.get("investorType"), type);
    }

    public static Specification<Investor> sector(String sector) {
        if (sector == null || sector.isBlank()) return null;
        return (root, query, cb) -> {
            query.distinct(true);
            jakarta.persistence.criteria.Join<Investor, String> join = root.join("sectors");
            return cb.equal(cb.lower(join), sector.trim().toLowerCase());
        };
    }

    public static Specification<Investor> stage(String stage) {
        if (stage == null || stage.isBlank()) return null;
        return (root, query, cb) -> {
            query.distinct(true);
            jakarta.persistence.criteria.Join<Investor, String> join = root.join("stages");
            return cb.equal(cb.lower(join), stage.trim().toLowerCase());
        };
    }

    public static Specification<Investor> location(String location) {
        if (location == null || location.isBlank()) return null;
        String like = LikePatterns.contains(location);
        return (root, query, cb) -> cb.like(cb.lower(cb.coalesce(root.get("location"), "")), like);
    }

    public static Specification<Investor> country(String country) {
        if (country == null || country.isBlank()) return null;
        return (root, query, cb) -> cb.equal(cb.lower(cb.coalesce(root.get("country"), "")), country.trim().toLowerCase());
    }

    /** Investors whose [chequeMin, chequeMax] range covers the given amount (an open end counts as unbounded). */
    public static Specification<Investor> chequeSize(Long amount) {
        if (amount == null) return null;
        return (root, query, cb) -> cb.and(
                cb.or(cb.isNull(root.get("chequeMin")), cb.lessThanOrEqualTo(root.get("chequeMin"), amount)),
                cb.or(cb.isNull(root.get("chequeMax")), cb.greaterThanOrEqualTo(root.get("chequeMax"), amount))
        );
    }
}
