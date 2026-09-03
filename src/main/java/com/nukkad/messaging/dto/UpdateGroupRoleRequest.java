package com.nukkad.messaging.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateGroupRoleRequest(@NotBlank String role) {
}
