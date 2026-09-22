package com.nukkad.investor.repository;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.investor.entity.Fundraise;
import com.nukkad.investor.entity.FundraiseStatus;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Objects;

public final class FundraiseSpecifications {

    private FundraiseSpecifications() {}

    @SafeVarargs
    public static Specification<Fundraise> combine(Specification<Fundraise>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<Fundraise> status(String statusLabel) {
        if (statusLabel == null || statusLabel.isBlank()) return null;
        FundraiseStatus status = FundraiseStatus.fromLabel(statusLabel.trim());
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Fundraise> stage(String stageLabel) {
        if (stageLabel == null || stageLabel.isBlank()) return null;
        StartupStage stage = StartupStage.fromLabel(stageLabel.trim());
        return (root, query, cb) -> cb.equal(root.get("fundingStage"), stage);
    }

    /** Excludes fundraises belonging to a startup that has switched fundraising visibility off,
     *  unless the viewer is an active team member of that same startup, and always excludes those of a startup an admin
     *  removed (the list-query twin of StartupAccessPolicy). Applied unconditionally in list() so a hidden fundraise's
     *  existence is never revealed by browsing all fundraises. */
    public static Specification<Fundraise> visibleTo(String viewerId) {
        return (root, query, cb) -> {
            var startupSub = query.subquery(String.class);
            var startup = startupSub.from(Startup.class);
            startupSub.select(startup.get("id")).where(cb.and(
                    cb.equal(startup.get("id"), root.get("startupId")),
                    cb.isTrue(startup.get("fundraisingVisible")),
                    cb.isFalse(startup.get("removedByAdmin")),
                    cb.equal(startup.get("moderationStatus"), ModerationStatus.APPROVED)
            ));
            var visibleByFlag = cb.exists(startupSub);

            if (viewerId == null) return visibleByFlag;

            var notRemovedSub = query.subquery(String.class);
            var liveStartup = notRemovedSub.from(Startup.class);
            notRemovedSub.select(liveStartup.get("id")).where(cb.and(
                    cb.equal(liveStartup.get("id"), root.get("startupId")),
                    cb.isFalse(liveStartup.get("removedByAdmin"))
            ));
            var notRemoved = cb.exists(notRemovedSub);

            var teamSub = query.subquery(String.class);
            var member = teamSub.from(StartupTeamMember.class);
            teamSub.select(member.get("startupId")).where(cb.and(
                    cb.equal(member.get("startupId"), root.get("startupId")),
                    cb.equal(member.get("userId"), viewerId),
                    cb.equal(member.get("status"), StartupTeamMember.Status.ACTIVE)
            ));
            var isTeamMember = cb.exists(teamSub);

            return cb.or(visibleByFlag, cb.and(isTeamMember, notRemoved));
        };
    }
}
