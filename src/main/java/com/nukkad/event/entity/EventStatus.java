package com.nukkad.event.entity;

import java.time.Instant;

/** Where an event is in time. There is no stored status: it follows from the start and end, so it can never go stale. */
public enum EventStatus {
    UPCOMING,
    LIVE,
    ENDED;

    public static EventStatus of(Event event, Instant now) {
        if (now.isBefore(event.getStartAt())) return UPCOMING;
        if (now.isAfter(event.getEndAt())) return ENDED;
        return LIVE;
    }
}
