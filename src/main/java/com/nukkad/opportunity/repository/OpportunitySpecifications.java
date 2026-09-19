package com.nukkad.opportunity.repository;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.opportunity.entity.Opportunity;
import com.nukkad.opportunity.entity.OpportunityType;
import com.nukkad.opportunity.entity.WorkMode;
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

    public static Specification<Opportunity> workMode(String workModeLabel) {
        if (workModeLabel == null || workModeLabel.isBlank()) return null;
        WorkMode workMode = WorkMode.fromLabel(workModeLabel.trim());
        return (root, query, cb) -> cb.equal(root.get("workMode"), workMode);
    }

    public static Specification<Opportunity> chapterId(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("chapterId"), chapterId);
    }

    public static Specification<Opportunity> startupId(String startupId) {
        if (startupId == null || startupId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("startupId"), startupId);
    }

    public static Specification<Opportunity> postedByUserId(String postedByUserId) {
        if (postedByUserId == null || postedByUserId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("postedByUserId"), postedByUserId);
    }

    /** Always applied in discovery/search — closed postings never appear in public browse results. */
    public static Specification<Opportunity> open() {
        return (root, query, cb) -> cb.isFalse(root.get("closed"));
    }

    public static Specification<Opportunity> notRemoved() {
        return (root, query, cb) -> cb.isFalse(root.get("removedByAdmin"));
    }

    /** Pre-publish gate for public discovery — see ModerationStatus. */
    public static Specification<Opportunity> approved() {
        return (root, query, cb) -> cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED);
    }

    public static Specification<Opportunity> moderationStatus(ModerationStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("moderationStatus"), status);
    }
}
