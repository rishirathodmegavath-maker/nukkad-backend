package com.nukkad.grant.repository;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.grant.entity.Grant;
import com.nukkad.grant.entity.GrantProviderType;
import com.nukkad.startup.entity.StartupStage;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public final class GrantSpecifications {

    private GrantSpecifications() {}

    @SafeVarargs
    public static Specification<Grant> combine(Specification<Grant>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<Grant> search(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("provider")), like),
                cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like)
        );
    }

    public static Specification<Grant> providerType(String typeLabel) {
        if (typeLabel == null || typeLabel.isBlank()) return null;
        GrantProviderType type = GrantProviderType.fromLabel(typeLabel.trim());
        return (root, query, cb) -> cb.equal(root.get("providerType"), type);
    }

    /** Matches grants open to this stage — including grants with no stage restriction at all. */
    public static Specification<Grant> stage(String stageLabel) {
        if (stageLabel == null || stageLabel.isBlank()) return null;
        StartupStage stage = StartupStage.fromLabel(stageLabel.trim());
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Grant, StartupStage> join = root.join("eligibleStages", JoinType.LEFT);
            return cb.or(cb.isEmpty(root.get("eligibleStages")), cb.equal(join, stage));
        };
    }

    /** Matches grants open to this sector — including grants with no sector restriction at all. */
    public static Specification<Grant> sector(String sector) {
        if (sector == null || sector.isBlank()) return null;
        String trimmed = sector.trim();
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Grant, String> join = root.join("eligibleSectors", JoinType.LEFT);
            return cb.or(cb.isEmpty(root.get("eligibleSectors")), cb.equal(cb.lower(join), trimmed.toLowerCase()));
        };
    }

    /** Excludes grants whose deadline has already passed — rolling (null-deadline) grants always pass. */
    public static Specification<Grant> notExpired() {
        return (root, query, cb) -> cb.or(cb.isNull(root.get("deadline")), cb.greaterThanOrEqualTo(root.get("deadline"), Instant.now()));
    }

    public static Specification<Grant> notRemoved() {
        return (root, query, cb) -> cb.isFalse(root.get("removedByAdmin"));
    }

    /** Pre-publish gate for public discovery — see ModerationStatus. */
    public static Specification<Grant> approved() {
        return (root, query, cb) -> cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED);
    }

    public static Specification<Grant> moderationStatus(ModerationStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("moderationStatus"), status);
    }
}
