package com.nukkad.program.dto;

import java.time.Instant;
import java.util.Map;

/** The applicant's own view of their application — deliberately excludes adminNote/reviewedBy:
 *  internal review notes are never exposed to the applicant (see AdminProgramApplicationDto for
 *  the admin-facing shape that does carry them). */
public record ProgramApplicationDto(
        String id,
        String program,
        String status,
        Map<String, String> answers,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
