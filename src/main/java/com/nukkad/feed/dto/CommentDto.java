package com.nukkad.feed.dto;

import java.time.Instant;

public record CommentDto(String id, String postId, String parentCommentId, String authorId, String content,
                          int replyCount, Instant createdAt) {
}
