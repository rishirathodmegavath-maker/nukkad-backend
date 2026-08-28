package com.nukkad.matching.dto;

import com.nukkad.user.dto.UserDto;

import java.util.List;

public record CofounderMatchDto(
        UserDto user,
        double score,
        int mutualConnections,
        List<String> reasons
) {
}
