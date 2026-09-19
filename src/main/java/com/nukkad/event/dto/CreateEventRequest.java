package com.nukkad.event.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreateEventRequest(
        @jakarta.validation.constraints.NotBlank @Size(max = 200) String title,
        String description,
        String chapterId,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        boolean online,
        @Size(max = 300) String location,
        @Size(max = 500) String meetingUrl,
        @Size(max = 500) String coverImageUrl,
        Integer capacity,
        List<String> startupIds
) {
}
