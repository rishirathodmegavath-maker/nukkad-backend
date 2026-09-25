package com.nukkad.startup.repository;

import com.nukkad.common.validation.LikePatterns;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.entity.StartupVisibility;
import jakarta.persistence.criteria.Predicate;
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

    /**
     * Tokenized multi-word search across name, tagline, sector, problem, solution and keywords.
     * Each whitespace-separated token must match at least one of those fields (case-insensitive,
     * partial); tokens are ANDed together so "AI healthcare" matches a startup whose tagline
     * contains "AI" and whose sector is "Healthcare", without requiring the whole phrase in one field.
     */
    public static Specification<Startup> search(String q) {
        if (q == null || q.isBlank()) return null;
        String[] tokens = q.trim().toLowerCase().split("\\s+");
        return (root, query, cb) -> {
            Predicate all = cb.conjunction();
            for (String token : tokens) {
                String like = LikePatterns.contains(token);
                Predicate anyField = cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("tagline"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("sector"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("problem"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("solution"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("keywords"), "")), like)
                );
                all = cb.and(all, anyField);
            }
            return all;
        };
    }

    public static Specification<Startup> sector(String sector) {
        if (sector == null || sector.isBlank()) return null;
        return (root, query, cb) -> cb.equal(cb.lower(cb.trim(root.get("sector"))), sector.trim().toLowerCase());
    }

    public static Specification<Startup> stage(String stageLabel) {
        if (stageLabel == null || stageLabel.isBlank()) return null;
        StartupStage stage = StartupStage.fromLabel(stageLabel.trim());
        return (root, query, cb) -> cb.equal(root.get("stage"), stage);
    }

    /** The raw stored flag. Only for callers that may see every startup as it really is (the admin listing). */
    public static Specification<Startup> isRaising(Boolean isRaising) {
        if (isRaising == null) return null;
        return (root, query, cb) -> cb.equal(root.get("isRaising"), isRaising);
    }

    /**
     * "Raising" as this viewer is allowed to see it. A startup only shows as raising to a viewer who may see its
     * fundraising: everyone while the founders keep fundraising visible, otherwise only its active team members
     * (the same rule StartupService applies when it builds the DTO's {@code isRaising}). So a startup with hidden
     * fundraising is never returned by {@code isRaising=true} to anyone else, and {@code isRaising=false} keeps
     * returning it, which means neither answer reveals that it is raising. {@code viewerId} is null for an anonymous caller.
     */
    public static Specification<Startup> isRaisingAsSeenBy(Boolean isRaising, String viewerId) {
        if (isRaising == null) return null;
        return (root, query, cb) -> {
            Predicate canSeeFundraising = cb.isTrue(root.get("fundraisingVisible"));
            if (viewerId != null) {
                var teams = query.subquery(String.class);
                var member = teams.from(StartupTeamMember.class);
                teams.select(member.get("startupId")).where(cb.and(
                        cb.equal(member.get("userId"), viewerId),
                        cb.equal(member.get("status"), StartupTeamMember.Status.ACTIVE)
                ));
                canSeeFundraising = cb.or(canSeeFundraising, root.get("id").in(teams));
            }
            Predicate shownAsRaising = cb.and(cb.isTrue(root.get("isRaising")), canSeeFundraising);
            return isRaising ? shownAsRaising : cb.not(shownAsRaising);
        };
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

    /** An anonymous (unauthenticated) viewer may only ever see PUBLIC startups; an authenticated
     *  viewer — any signed-in Nukkad user — may see both PUBLIC and NUKKAD_MEMBERS startups. */
    public static Specification<Startup> visibleTo(boolean authenticated) {
        if (authenticated) return null;
        return (root, query, cb) -> cb.equal(root.get("visibility"), StartupVisibility.PUBLIC);
    }

    public static Specification<Startup> notRemoved() {
        return (root, query, cb) -> cb.isFalse(root.get("removedByAdmin"));
    }

    /** Pre-publish gate for public discovery — see ModerationStatus. */
    public static Specification<Startup> approved() {
        return (root, query, cb) -> cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED);
    }

    public static Specification<Startup> moderationStatus(ModerationStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("moderationStatus"), status);
    }
}
