package com.nukkad.admin.dto;

import com.nukkad.program.catalog.ProgramBenefit;
import com.nukkad.program.catalog.ProgramJourneyPhase;
import com.nukkad.program.catalog.ProgramStep;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

/** Every list is optional and defaults to empty — a freshly created DRAFT program legitimately has
 *  no journey/benefits/application questions yet; nothing here is ever fabricated to fill a gap.
 *  {@code @Builder} is for tests only — Jackson still deserializes a JSON request body through the
 *  plain canonical constructor. */
@Builder
public record CreateProgramRequest(
        @NotBlank @Size(max = 60) String slug,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 40) String badge,
        @NotBlank @Size(max = 220) String tagline,
        @NotBlank String description,
        String heroImageUrl,
        String thumbnailUrl,
        /** DRAFT / PUBLISHED / ARCHIVED — defaults to DRAFT when blank, never publishes by accident. */
        String status,
        Integer displayOrder,
        List<String> highlights,
        List<String> targetAudience,
        @Size(max = 500) String audienceDescription,
        @Size(max = 200) String eligibilityTitle,
        @Size(max = 500) String eligibilityDescription,
        List<String> eligibilityPoints,
        List<ProgramJourneyPhase> journey,
        List<ProgramBenefit> benefits,
        @Size(max = 200) String outcomeHeading,
        String outcomeDescription,
        List<ProgramStep> applicationSteps,
        Boolean applicationOpen,
        Integer feeAmount,
        @Size(max = 10) String feeCurrency,
        @Size(max = 500) String enrollmentInfo,
        Boolean selective
) {
}
