package com.nukkad.program.catalog;

import java.util.List;

/** One step of the application wizard. {@code id} is a stable slug used in the frontend URL/state
 *  (e.g. {@code ?step=background}), independent of display order so steps could be reordered later
 *  without breaking an in-progress applicant's bookmarked URL. */
public record ProgramStep(String id, String title, List<ProgramField> fields) {
}
