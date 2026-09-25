package com.nukkad.feed.dto;

import java.time.Instant;
import java.util.List;

/** {@code savedAt} is only populated by the saved-posts listing (when this post was saved by the
 * viewer) — null everywhere else, including the regular feed listing. {@code postedAsPlatform}
 * marks an admin-published, unattributed post: the public author to show is {@code publisherIdentity}
 * (e.g. "BuildAdda Insights"), not the admin account behind {@code authorId}; ignore
 * {@code publisherIdentity} when {@code postedAsPlatform} is false. {@code likesCount} is always the
 * real, togglable count — {@code platformEngagementCount} is a separate seeded number the client adds
 * on for display (never a real Like, never returned by the liker list). */
public record PostDto(String id, String authorId, String type, String content, String relatedId,
                       int likesCount, int commentsCount, boolean isLiked, boolean isSaved,
                       boolean hideLikeCount, boolean commentsDisabled, Instant createdAt,
                       List<AttachmentDto> attachments, Instant savedAt,
                       boolean removedByAdmin, String removalReason,
                       String visibility, String linkUrl, boolean postedAsPlatform,
                       String publisherIdentity, int platformEngagementCount) {
}
