package com.nukkad.feed.dto;

import java.util.List;

/** The personalized feed's response shape — deliberately not a {@code PageResponse}: there is no
 *  stable "total" for a live-scored, dynamically-reranked feed, and no major feed product exposes
 *  one either. {@code hasMore} is the only pagination signal; the client passes back every id it
 *  has already been shown as {@code excludeIds} to fetch the next batch. */
public record PersonalizedFeedResultDto(List<PostDto> content, boolean hasMore) {
}
