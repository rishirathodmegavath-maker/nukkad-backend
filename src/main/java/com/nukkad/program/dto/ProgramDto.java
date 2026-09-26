package com.nukkad.program.dto;

import com.nukkad.program.catalog.ProgramJourneyPhase;
import com.nukkad.program.catalog.ProgramStep;

import java.util.List;

/** Merges {@link com.nukkad.program.catalog.ProgramCatalog}'s fixed copy with the program's live,
 *  admin-editable {@link com.nukkad.program.entity.ProgramSettings} row into the one shape both the
 *  landing page (as a summary) and the detail page (in full) fetch. {@code feeAmount}/{@code
 *  enrollmentInfo}/{@code selective} are null until Admin sets them — never a fabricated default. */
public record ProgramDto(
        String key,
        String name,
        String tagline,
        String description,
        List<String> highlights,
        List<String> targetAudience,
        List<ProgramJourneyPhase> journey,
        List<String> benefits,
        String outcome,
        List<ProgramStep> applicationSteps,
        boolean applicationOpen,
        Integer feeAmount,
        String feeCurrency,
        String enrollmentInfo,
        Boolean selective
) {
}
