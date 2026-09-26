package com.nukkad.admin.dto;

import java.time.Instant;
import java.util.Map;

/** Unlike {@link com.nukkad.program.dto.ProgramApplicationDto} (the applicant's own view), this
 *  carries the internal review note and reviewer identity — never sent to the applicant. */
public record AdminProgramApplicationDto(
        String id,
        String applicantUserId,
        String applicantName,
        String applicantEmail,
        String program,
        String status,
        Map<String, String> answers,
        Instant submittedAt,
        String adminNote,
        String reviewedBy,
        String reviewedByName,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
