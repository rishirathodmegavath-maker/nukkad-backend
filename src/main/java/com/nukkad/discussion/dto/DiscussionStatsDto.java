package com.nukkad.discussion.dto;

/** Real platform-wide aggregates for the Discussions sidebar — never fabricated placeholder numbers. */
public record DiscussionStatsDto(long totalDiscussions, long totalReplies, long totalParticipants) {
}
