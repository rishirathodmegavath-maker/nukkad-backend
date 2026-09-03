package com.nukkad.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMessageRequest(@NotBlank @Size(max = 4000) String content) {
}
