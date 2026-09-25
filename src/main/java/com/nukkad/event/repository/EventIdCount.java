package com.nukkad.event.repository;

/** One grouped-count row (attendees grouped by event id) — lets a list endpoint fetch every row's
 *  attendee count in one query instead of one query per event. */
public interface EventIdCount {
    String getEventId();

    long getTotal();
}
