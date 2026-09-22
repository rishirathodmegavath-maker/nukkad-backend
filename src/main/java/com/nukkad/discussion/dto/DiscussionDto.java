package com.nukkad.discussion.dto;

import com.nukkad.feed.dto.AttachmentDto;

import java.time.Instant;
import java.util.List;

/**
 * A discussion as its viewer sees it. {@code content} is the one real free-text field — there is no
 * separate "title" in the data model, so the frontend derives a headline from it the same way it
 * always has (no backend change needed for that). Every count here is real: {@code netScore} is
 * {@code SUM(post_votes.value)}, {@code viewsCount} is a row count, {@code participantCount} is
 * {@code |{authorId} ∪ {distinct reply authorIds}|} — nothing here is fabricated.
 *
 * @param topic         null for a discussion made through the plain Feed composer (no topic picker there) — the
 *                       frontend shows that as "General" rather than requiring a value.
 * @param lastActivityAt the later of the discussion's own createdAt and its most recent reply's createdAt.
 * @param myVote        -1, 0 or 1 — this viewer's own vote, never anyone else's.
 */
public record DiscussionDto(
        String id, String authorId, String content, String visibility, String linkUrl,
        String topic, String topicLabel, List<String> tags,
        int likesCount, boolean isLiked, boolean isSaved,
        int commentsCount, int netScore, int myVote,
        long viewsCount, int participantCount, boolean isFollowing,
        boolean commentsDisabled, List<AttachmentDto> attachments,
        boolean removedByAdmin, String removalReason,
        Instant createdAt, Instant lastActivityAt
) {
}
