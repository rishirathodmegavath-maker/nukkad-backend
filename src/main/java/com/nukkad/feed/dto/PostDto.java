package com.nukkad.feed.dto;

import java.time.Instant;
import java.util.List;

/** {@code savedAt} is only populated by the saved-posts listing (when this post was saved by the
 * viewer) — null everywhere else, including the regular feed listing. */
public record PostDto(String id, String authorId, String type, String content, String relatedId,
                       int likesCount, int commentsCount, boolean isLiked, boolean isSaved,
                       boolean hideLikeCount, boolean commentsDisabled, Instant createdAt,
                       List<AttachmentDto> attachments, Instant savedAt) {
}
