package com.nukkad.program.repository;

import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramApplication;
import com.nukkad.program.entity.ProgramApplicationStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/** Admin list filters for {@link ProgramApplication} — mirrors the {@code *Specifications} shape
 *  used elsewhere (e.g. {@code UserSpecifications}). "Search applicant" has no column of its own:
 *  {@link com.nukkad.admin.service.AdminProgramApplicationService} resolves a name/email query to
 *  a set of user ids first (via {@code UserSpecifications.adminSearch}), then narrows by {@link
 *  #applicantIn}. */
public final class ProgramApplicationSpecifications {

    private ProgramApplicationSpecifications() {}

    @SafeVarargs
    public static Specification<ProgramApplication> combine(Specification<ProgramApplication>... specs) {
        return java.util.Arrays.stream(specs)
                .filter(java.util.Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<ProgramApplication> program(Program program) {
        if (program == null) return null;
        return (root, query, cb) -> cb.equal(root.get("program"), program);
    }

    public static Specification<ProgramApplication> status(ProgramApplicationStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<ProgramApplication> applicantIn(Collection<String> userIds) {
        if (userIds == null) return null;
        return (root, query, cb) -> root.get("applicantUserId").in(userIds);
    }
}
