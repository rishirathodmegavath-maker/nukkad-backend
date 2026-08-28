package com.nukkad.startup.dto;

import java.time.Instant;

public record StartupUpdateDto(String id, String startupId, String content, Instant createdAt) {
}
