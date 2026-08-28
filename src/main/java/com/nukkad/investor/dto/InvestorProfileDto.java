package com.nukkad.investor.dto;

import com.nukkad.user.dto.UserDto;

import java.time.Instant;
import java.util.Set;

public record InvestorProfileDto(
        String id,
        String userId,
        UserDto user,
        String investorType,
        String firmName,
        String thesis,
        Set<String> sectors,
        Set<String> stages,
        Set<String> geographies,
        Long ticketMin,
        Long ticketMax,
        int portfolioCount,
        String website,
        boolean canManage,
        Instant createdAt,
        Instant updatedAt
) {
}
