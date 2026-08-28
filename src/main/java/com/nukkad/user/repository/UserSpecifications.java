package com.nukkad.user.repository;

import com.nukkad.user.entity.LookingFor;
import com.nukkad.user.entity.User;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

    private UserSpecifications() {}

    /** Specification.allOf/anyOf reject null elements (unlike the old .and() chain), so filter first. */
    @SafeVarargs
    public static Specification<User> combine(Specification<User>... specs) {
        return java.util.Arrays.stream(specs)
                .filter(java.util.Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<User> excludeId(String id) {
        return (root, query, cb) -> id == null ? null : cb.notEqual(root.get("id"), id);
    }

    public static Specification<User> excludeIds(java.util.Set<String> ids) {
        return (root, query, cb) -> ids == null || ids.isEmpty() ? null : cb.not(root.get("id").in(ids));
    }

    public static Specification<User> search(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(cb.coalesce(root.get("headline"), ""))
                        , like),
                cb.like(cb.lower(cb.coalesce(root.get("bio"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("collegeOrCompany"), "")), like)
        );
    }

    public static Specification<User> hasSkill(String skill) {
        if (skill == null || skill.isBlank()) return null;
        return (root, query, cb) -> {
            query.distinct(true);
            jakarta.persistence.criteria.Join<User, String> join = root.join("skills");
            return cb.equal(cb.lower(join), skill.trim().toLowerCase());
        };
    }

    public static Specification<User> location(String location) {
        if (location == null || location.isBlank()) return null;
        String like = "%" + location.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(cb.coalesce(root.get("location"), "")), like);
    }

    public static Specification<User> collegeOrCompany(String value) {
        if (value == null || value.isBlank()) return null;
        String like = "%" + value.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(cb.coalesce(root.get("collegeOrCompany"), "")), like);
    }

    public static Specification<User> role(String value) {
        if (value == null || value.isBlank()) return null;
        String like = "%" + value.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(cb.coalesce(root.get("role"), "")), like);
    }

    public static Specification<User> lookingFor(String value) {
        if (value == null || value.isBlank()) return null;
        LookingFor target;
        try {
            target = java.util.Arrays.stream(LookingFor.values())
                    .filter(v -> v.getLabel().equalsIgnoreCase(value.trim()))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            target = null;
        }
        if (target == null) return null;
        LookingFor finalTarget = target;
        return (root, query, cb) -> {
            query.distinct(true);
            jakarta.persistence.criteria.Join<User, LookingFor> join = root.join("lookingFor");
            return cb.equal(join, finalTarget);
        };
    }

    public static Specification<User> minExperience(Integer minExperience) {
        if (minExperience == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("experienceYears"), minExperience);
    }

    public static Specification<User> chapterId(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("chapterId"), chapterId);
    }
}
