package com.nukkad.investor.repository;

import com.nukkad.investor.entity.InvestorProfile;
import com.nukkad.investor.entity.InvestorType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Objects;

public final class InvestorProfileSpecifications {

    private InvestorProfileSpecifications() {}

    @SafeVarargs
    public static Specification<InvestorProfile> combine(Specification<InvestorProfile>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<InvestorProfile> search(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(cb.coalesce(root.get("firmName"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("thesis"), "")), like)
        );
    }

    public static Specification<InvestorProfile> type(String typeLabel) {
        if (typeLabel == null || typeLabel.isBlank()) return null;
        InvestorType type = InvestorType.fromLabel(typeLabel.trim());
        return (root, query, cb) -> cb.equal(root.get("investorType"), type);
    }

    public static Specification<InvestorProfile> sector(String sector) {
        if (sector == null || sector.isBlank()) return null;
        return (root, query, cb) -> {
            query.distinct(true);
            jakarta.persistence.criteria.Join<InvestorProfile, String> join = root.join("sectors");
            return cb.equal(cb.lower(join), sector.trim().toLowerCase());
        };
    }

    public static Specification<InvestorProfile> stage(String stage) {
        if (stage == null || stage.isBlank()) return null;
        return (root, query, cb) -> {
            query.distinct(true);
            jakarta.persistence.criteria.Join<InvestorProfile, String> join = root.join("stages");
            return cb.equal(cb.lower(join), stage.trim().toLowerCase());
        };
    }

    public static Specification<InvestorProfile> geography(String geography) {
        if (geography == null || geography.isBlank()) return null;
        return (root, query, cb) -> {
            query.distinct(true);
            jakarta.persistence.criteria.Join<InvestorProfile, String> join = root.join("geographies");
            return cb.equal(cb.lower(join), geography.trim().toLowerCase());
        };
    }

    /** Investors whose [ticketMin, ticketMax] range covers the given amount (an open end counts as unbounded). */
    public static Specification<InvestorProfile> ticketSize(Long amount) {
        if (amount == null) return null;
        return (root, query, cb) -> cb.and(
                cb.or(cb.isNull(root.get("ticketMin")), cb.lessThanOrEqualTo(root.get("ticketMin"), amount)),
                cb.or(cb.isNull(root.get("ticketMax")), cb.greaterThanOrEqualTo(root.get("ticketMax"), amount))
        );
    }
}
