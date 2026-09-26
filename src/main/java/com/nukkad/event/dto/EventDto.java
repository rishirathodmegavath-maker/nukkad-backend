package com.nukkad.event.dto;

import com.nukkad.event.entity.EventStatus;

import java.time.Instant;
import java.util.List;

public record EventDto(
        String id,
        String title,
        String description,
        String chapterId,
        String chapterName,
        String organizerUserId,
        boolean postedAsPlatform,
        String publisherIdentity,
        Instant startAt,
        Instant endAt,
        /** Where the event is in time, worked out by the server from start and end (never stored, so never stale). */
        EventStatus status,
        boolean online,
        String location,
        String meetingUrl,
        String coverImageUrl,
        Integer capacity,
        long attendeeCount,
        boolean isAttending,
        boolean canManage,
        List<EventStartupSummaryDto> startups,
        Instant createdAt,
        Instant updatedAt
) {
}
