package com.nukkad.investor.service;

import java.util.List;

/** What {@link InvestorCsvParser} found before anything is validated against the database — the "detect/map
 *  columns" step, plus every row parsed and normalized. */
public record InvestorCsvParseResult(
        List<String> headers,
        List<String> unrecognizedHeaders,
        boolean hasNameColumn,
        boolean hasIdColumn,
        List<InvestorCsvRow> rows,
        /** Non-null only for an Excel upload with more than one sheet — see {@link InvestorCsvParser#parseExcel}. */
        String note
) {
}
