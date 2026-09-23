package com.nukkad.grant.dto;

import java.util.List;

public record GrantImportPreviewRowDto(
        int rowNumber,
        String name,
        String provider,
        String providerType,
        String deadline,
        List<String> warnings,
        String error
) {
}
