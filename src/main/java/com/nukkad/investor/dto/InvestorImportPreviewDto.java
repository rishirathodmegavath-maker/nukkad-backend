package com.nukkad.investor.dto;

import java.util.List;

/** The "detect/map columns, preview records" step — nothing is saved yet. Built from the whole file (so
 *  {@code totalRows} is accurate) but only the first few rows come back in {@code sampleRows}. */
public record InvestorImportPreviewDto(
        int totalRows,
        List<String> detectedColumns,
        List<String> unrecognizedColumns,
        boolean hasIdColumn,
        List<InvestorImportPreviewRowDto> sampleRows
) {
}
