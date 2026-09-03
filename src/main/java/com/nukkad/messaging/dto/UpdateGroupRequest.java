package com.nukkad.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(@NotBlank @Size(max = 100) String name) {
}
