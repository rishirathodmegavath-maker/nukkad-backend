package com.nukkad.event.dto;

import java.time.Instant;

public record EventDto(
        String id,
        String title,
        String description,
        String chapterId,
        String chapterName,
        String organizerUserId,
        Instant startAt,
        Instant endAt,
        boolean online,
        String location,
        String meetingUrl,
        String coverImageUrl,
        Integer capacity,
        long attendeeCount,
        boolean isAttending,
        boolean canManage,
        Instant createdAt,
        Instant updatedAt
) {
}
