package com.nukkad.idea.repository;

import com.nukkad.idea.entity.ContributionArea;
import com.nukkad.idea.entity.Idea;
import com.nukkad.idea.entity.IdeaStage;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Objects;

public final class IdeaSpecifications {

    private IdeaSpecifications() {}

    @SafeVarargs
    public static Specification<Idea> combine(Specification<Idea>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<Idea> search(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("problem")), like),
                cb.like(cb.lower(root.get("solution")), like)
        );
    }

    public static Specification<Idea> stage(String stageLabel) {
        if (stageLabel == null || stageLabel.isBlank()) return null;
        IdeaStage stage = IdeaStage.fromLabel(stageLabel.trim());
        return (root, query, cb) -> cb.equal(root.get("stage"), stage);
    }

    public static Specification<Idea> category(String category) {
        if (category == null || category.isBlank()) return null;
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase());
    }

    public static Specification<Idea> helpNeeded(String contributionAreaLabel) {
        if (contributionAreaLabel == null || contributionAreaLabel.isBlank()) return null;
        ContributionArea area = ContributionArea.fromLabel(contributionAreaLabel.trim());
        return (root, query, cb) -> {
            query.distinct(true);
            jakarta.persistence.criteria.Join<Idea, ContributionArea> join = root.join("helpNeeded");
            return cb.equal(join, area);
        };
    }

    public static Specification<Idea> chapterId(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("chapterId"), chapterId);
    }

    public static Specification<Idea> creatorId(String creatorId) {
        if (creatorId == null || creatorId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("creatorId"), creatorId);
    }

    public static Specification<Idea> notConvertedToStartup() {
        return (root, query, cb) -> cb.isNull(root.get("startupId"));
    }
}
