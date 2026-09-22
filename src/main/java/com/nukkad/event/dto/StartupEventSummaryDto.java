package com.nukkad.event.dto;

import com.nukkad.event.entity.EventStatus;

import java.time.Instant;

/** One event on a startup's profile: enough to show it as a card and open it. */
public record StartupEventSummaryDto(
        String id,
        String title,
        Instant startAt,
        Instant endAt,
        boolean online,
        String location,
        String coverImageUrl,
        /** The chapter that runs the event, or null for an event a member organises on their own. */
        String chapterName,
        EventStatus status
) {
}
