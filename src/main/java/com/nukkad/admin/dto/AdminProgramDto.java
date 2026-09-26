package com.nukkad.admin.dto;

import com.nukkad.program.catalog.ProgramBenefit;
import com.nukkad.program.catalog.ProgramJourneyPhase;
import com.nukkad.program.catalog.ProgramStep;

import java.time.Instant;
import java.util.List;

/** Admin's full view of a program — everything {@link com.nukkad.program.dto.ProgramDto} carries
 *  plus what only Admin needs to see: its id (for edit/delete), status (including DRAFT), and how
 *  many applications it has received (excluding never-submitted drafts, which aren't really
 *  "applications" yet). */
public record AdminProgramDto(
        String id,
        String slug,
        String name,
        String badge,
        String tagline,
        String description,
        String heroImageUrl,
        String thumbnailUrl,
        String status,
        int displayOrder,
        List<String> highlights,
        List<String> targetAudience,
        String audienceDescription,
        String eligibilityTitle,
        String eligibilityDescription,
        List<String> eligibilityPoints,
        List<ProgramJourneyPhase> journey,
        List<ProgramBenefit> benefits,
        String outcomeHeading,
        String outcomeDescription,
        List<ProgramStep> applicationSteps,
        boolean applicationOpen,
        Integer feeAmount,
        String feeCurrency,
        String enrollmentInfo,
        Boolean selective,
        long applicationCount,
        Instant createdAt,
        Instant updatedAt
) {
}
