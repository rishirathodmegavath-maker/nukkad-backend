package com.nukkad.discussion.dto;

/** One row of "Popular Topics" — {@code count} is a real, live {@code COUNT(*)} over public discussions,
 *  never a placeholder. */
public record TopicCountDto(String topic, String label, long count) {
}
