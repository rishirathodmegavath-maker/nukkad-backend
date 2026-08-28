package com.nukkad.startup.dto;

import jakarta.validation.constraints.NotBlank;

public record AddTeamMemberRequest(@NotBlank String userId, String roleId) {
}
