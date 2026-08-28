package com.nukkad.event.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateEventRequest(
        @Size(max = 200) String title,
        String description,
        Instant startAt,
        Instant endAt,
        Boolean online,
        @Size(max = 300) String location,
        @Size(max = 500) String meetingUrl,
        @Size(max = 500) String coverImageUrl,
        Integer capacity
) {
}
