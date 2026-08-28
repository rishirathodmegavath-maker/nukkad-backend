package com.nukkad.messaging.dto;

import jakarta.validation.constraints.NotBlank;

public record StartConversationRequest(@NotBlank String otherUserId) {
}
