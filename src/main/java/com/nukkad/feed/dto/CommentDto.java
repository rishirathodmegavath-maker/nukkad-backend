package com.nukkad.feed.dto;

import java.time.Instant;

public record CommentDto(String id, String postId, String authorId, String content, Instant createdAt) {
}
