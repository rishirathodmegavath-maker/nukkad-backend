package com.nukkad.feed.dto;

import java.time.Instant;
import java.util.List;

public record PostDto(String id, String authorId, String type, String content, String relatedId,
                       int likesCount, int commentsCount, boolean isLiked, boolean isSaved,
                       boolean hideLikeCount, boolean commentsDisabled, Instant createdAt,
                       List<AttachmentDto> attachments) {
}
