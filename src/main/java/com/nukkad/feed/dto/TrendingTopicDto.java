package com.nukkad.feed.dto;

/** A hashtag (lowercase, no "#") and the number of recent posts that used it. */
public record TrendingTopicDto(String tag, long postCount) {
}
