package com.nukkad.startup.repository;

import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Objects;

public final class StartupSpecifications {

    private StartupSpecifications() {}

    @SafeVarargs
    public static Specification<Startup> combine(Specification<Startup>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<Startup> search(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(cb.coalesce(root.get("tagline"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("problem"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("solution"), "")), like)
        );
    }

    public static Specification<Startup> sector(String sector) {
        if (sector == null || sector.isBlank()) return null;
        return (root, query, cb) -> cb.equal(cb.lower(root.get("sector")), sector.trim().toLowerCase());
    }

    public static Specification<Startup> stage(String stageLabel) {
        if (stageLabel == null || stageLabel.isBlank()) return null;
        StartupStage stage = StartupStage.fromLabel(stageLabel.trim());
        return (root, query, cb) -> cb.equal(root.get("stage"), stage);
    }

    public static Specification<Startup> isRaising(Boolean isRaising) {
        if (isRaising == null) return null;
        return (root, query, cb) -> cb.equal(root.get("isRaising"), isRaising);
    }

    public static Specification<Startup> chapterId(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("chapterId"), chapterId);
    }

    /** Startups this user is an active team member of (used for "startups on their profile"). */
    public static Specification<Startup> memberId(String userId) {
        if (userId == null || userId.isBlank()) return null;
        return (root, query, cb) -> {
            var subquery = query.subquery(String.class);
            var member = subquery.from(StartupTeamMember.class);
            subquery.select(member.get("startupId")).where(cb.and(
                    cb.equal(member.get("userId"), userId),
                    cb.equal(member.get("status"), StartupTeamMember.Status.ACTIVE)
            ));
            return root.get("id").in(subquery);
        };
    }
}
