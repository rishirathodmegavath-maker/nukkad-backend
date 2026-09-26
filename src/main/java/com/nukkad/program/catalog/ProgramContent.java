package com.nukkad.program.catalog;

import com.nukkad.program.entity.Program;

import java.util.List;

/** The fixed, code-defined content for one program (see {@link ProgramCatalog}'s doc comment for
 *  why this is code rather than an admin-editable CMS). Every list here is copy taken directly from
 *  the product spec, never fabricated. */
public record ProgramContent(
        Program program,
        String name,
        String tagline,
        String description,
        List<String> highlights,
        List<String> targetAudience,
        List<ProgramJourneyPhase> journey,
        List<String> benefits,
        String outcome,
        List<ProgramStep> applicationSteps
) {
}
