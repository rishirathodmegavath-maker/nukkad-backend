package com.nukkad.matching.dto;

import com.nukkad.user.dto.UserDto;

import java.util.List;

public record RecommendedUserDto(
        UserDto user,
        double score,
        int mutualConnections,
        List<String> commonSkills,
        Integer graphDistance,
        List<String> reasons
) {
}
