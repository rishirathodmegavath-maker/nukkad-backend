package com.nukkad.investor.dto;

import java.util.List;

public record InvestorImportPreviewRowDto(
        int rowNumber,
        String externalSourceId,
        String name,
        String investorType,
        String location,
        String country,
        List<String> warnings,
        String error
) {
}
