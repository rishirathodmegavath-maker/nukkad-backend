package com.nukkad.program.catalog;

/** One step of a program's journey. {@code detailedDescription}, {@code outcome} and {@code icon} are
 *  optional — null rather than fabricated when Admin hasn't filled them in (see {@code Program}'s own
 *  doc comment on not inventing content). {@code icon} is a lucide-react icon name the frontend already
 *  knows, not a stored image. */
public record ProgramJourneyPhase(
        int number,
        String title,
        String description,
        String detailedDescription,
        String outcome,
        String icon
) {
    public ProgramJourneyPhase(int number, String title, String description) {
        this(number, title, description, null, null, null);
    }
}
