package com.nukkad.startup.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMemberRoleRequest(@NotBlank String teamRole) {
}
