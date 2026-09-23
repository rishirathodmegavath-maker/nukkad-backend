package com.nukkad.grant.service;

import java.util.List;

public record GrantCsvParseResult(
        List<String> headers,
        List<String> unrecognizedHeaders,
        List<GrantCsvRow> rows,
        /** Non-null only for a multi-sheet Excel upload. */
        String note
) {
}
