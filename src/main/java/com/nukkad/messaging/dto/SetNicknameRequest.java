package com.nukkad.messaging.dto;

import jakarta.validation.constraints.Size;

public record SetNicknameRequest(@Size(max = 50) String nickname) {
}
