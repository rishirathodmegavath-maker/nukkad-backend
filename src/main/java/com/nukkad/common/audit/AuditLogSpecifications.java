package com.nukkad.common.audit;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    @SafeVarargs
    public static Specification<AuditLog> combine(Specification<AuditLog>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<AuditLog> actorId(String userId) {
        if (userId == null || userId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<AuditLog> action(AuditAction action) {
        if (action == null) return null;
        return (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> entityType(String entityType) {
        if (entityType == null || entityType.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("entityType"), entityType);
    }

    public static Specification<AuditLog> entityId(String entityId) {
        if (entityId == null || entityId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("entityId"), entityId);
    }

    public static Specification<AuditLog> createdAfter(Instant from) {
        if (from == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<AuditLog> createdBefore(Instant to) {
        if (to == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
