package com.nukkad.event.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record UpdateEventRequest(
        // Optional (null leaves the title alone), but a title that is sent can't be empty or only spaces.
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 200) String title,
        String description,
        Instant startAt,
        Instant endAt,
        Boolean online,
        @Size(max = 300) String location,
        @Size(max = 500) String meetingUrl,
        @Size(max = 500) String coverImageUrl,
        @Positive Integer capacity,
        List<String> startupIds
) {
}
