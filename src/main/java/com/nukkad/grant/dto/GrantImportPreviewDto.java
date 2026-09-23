package com.nukkad.grant.dto;

import java.util.List;

/** The "detect/map columns, preview records" step — nothing is saved yet. Built from the whole
 *  file (so {@code totalRows} is accurate) but only the first few rows come back in {@code sampleRows}. */
public record GrantImportPreviewDto(
        int totalRows,
        List<String> detectedColumns,
        List<String> unrecognizedColumns,
        /** Non-null only for a multi-sheet Excel upload — only the first sheet is ever imported. */
        String note,
        List<GrantImportPreviewRowDto> sampleRows
) {
}
