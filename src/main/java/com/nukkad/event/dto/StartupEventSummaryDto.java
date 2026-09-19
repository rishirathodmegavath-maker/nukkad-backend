package com.nukkad.event.dto;

import java.time.Instant;

public record StartupEventSummaryDto(
        String id,
        String title,
        Instant startAt,
        Instant endAt,
        boolean online,
        String location
) {
}
