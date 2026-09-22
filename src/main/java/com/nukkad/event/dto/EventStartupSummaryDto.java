package com.nukkad.event.dto;

/** A startup taking part in an event. {@code canUnlink}: the viewer may take it off the event (they run the event, or manage the startup). */
public record EventStartupSummaryDto(String id, String name, String logoUrl, boolean canUnlink) {
}
