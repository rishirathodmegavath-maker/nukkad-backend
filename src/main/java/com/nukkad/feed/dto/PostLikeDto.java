package com.nukkad.feed.dto;

import java.time.Instant;

public record PostLikeDto(String userId, Instant createdAt) {
}
