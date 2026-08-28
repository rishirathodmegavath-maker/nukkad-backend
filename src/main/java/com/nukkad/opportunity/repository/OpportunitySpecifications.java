package com.nukkad.opportunity.repository;

import com.nukkad.opportunity.entity.Opportunity;
import com.nukkad.opportunity.entity.OpportunityType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Objects;

public final class OpportunitySpecifications {

    private OpportunitySpecifications() {}

    @SafeVarargs
    public static Specification<Opportunity> combine(Specification<Opportunity>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<Opportunity> search(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("description")), like),
                cb.like(cb.lower(root.get("organizationName")), like)
        );
    }

    public static Specification<Opportunity> type(String typeLabel) {
        if (typeLabel == null || typeLabel.isBlank()) return null;
        OpportunityType type = OpportunityType.fromLabel(typeLabel.trim());
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Opportunity> remote(Boolean remote) {
        if (remote == null) return null;
        return (root, query, cb) -> cb.equal(root.get("remote"), remote);
    }

    public static Specification<Opportunity> chapterId(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("chapterId"), chapterId);
    }
}
