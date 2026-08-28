package com.nukkad.investor.repository;

import com.nukkad.investor.entity.Fundraise;
import com.nukkad.investor.entity.FundraiseStatus;
import com.nukkad.startup.entity.StartupStage;
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
}
