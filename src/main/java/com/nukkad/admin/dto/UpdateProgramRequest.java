package com.nukkad.admin.dto;

import com.nukkad.program.catalog.ProgramBenefit;
import com.nukkad.program.catalog.ProgramJourneyPhase;
import com.nukkad.program.catalog.ProgramStep;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

/** Every field is optional: null means "leave unchanged", not "clear this" — except the list fields
 *  (highlights, journey, benefits, applicationSteps, ...), which are always sent as a complete
 *  replacement of the whole list by the admin form (the same "the caller owns the whole collection"
 *  shape {@code ProgramApplication.answers}'s draft-merge deliberately does NOT use, because here
 *  there is no autosave-by-field to protect against — see {@code AdminProgramService#update}).
 *  {@code @Builder} is for tests only (named fields instead of a 27-argument positional call) —
 *  Jackson still deserializes a JSON request body through the plain canonical constructor. */
@Builder
public record UpdateProgramRequest(
        @Size(max = 60) String slug,
        @Size(max = 120) String name,
        @Size(max = 40) String badge,
        @Size(max = 220) String tagline,
        String description,
        String heroImageUrl,
        Boolean removeHeroImage,
        String thumbnailUrl,
        Boolean removeThumbnail,
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
