package com.nukkad.investor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateInvestorProfileRequest(
        @NotBlank String investorType,
        @Size(max = 200) String firmName,
        String thesis,
        Set<String> sectors,
        Set<String> stages,
        Set<String> geographies,
        Long ticketMin,
        Long ticketMax,
        Integer portfolioCount,
        @Size(max = 300) String website
) {
}
