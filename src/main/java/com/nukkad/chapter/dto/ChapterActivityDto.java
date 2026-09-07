package com.nukkad.chapter.dto;

import java.time.Instant;

/**
 * A single row in a chapter's "recent activity" feed. This is a read-only presentation view over
 * existing entities (Idea/Startup/Opportunity/Event/Resource, all already chapter-scoped via their
 * own chapterId, plus User.chapterJoinedAt for membership) — no new activity-log table backs this.
 *
 * @param type       one of IDEA, STARTUP, OPPORTUNITY, EVENT, RESOURCE, MEMBER_JOINED
 * @param entityId   id of the underlying idea/startup/opportunity/event/resource, or the user id for MEMBER_JOINED
 * @param title      the idea/startup/opportunity/event/resource title — null for MEMBER_JOINED
 * @param actorUserId the user who performed the action (creator/poster/organizer/uploader/joiner)
 */
public record ChapterActivityDto(
        String type,
        String entityId,
        String title,
        String actorUserId,
        String actorName,
        String actorAvatarUrl,
        Instant occurredAt
) {
}
