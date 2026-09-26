package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * An admin publishing an event from the admin panel. Same fields as a member's own event (see
 * CreateEventRequest), minus a startup attribution — that association still requires the linking
 * user to manage each startup, which an unattributed platform event has no real owner to satisfy.
 * {@code organizerEmail} is optional: with it, that member is attributed as the organizer and is
 * told; without it, the admin's own account is, and {@code publisherIdentity} picks which platform
 * identity to display instead — ignored when organizerEmail is set.
 */
public record AdminCreateEventRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        String chapterId,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        boolean online,
        @Size(max = 300) String location,
        @Size(max = 500) String meetingUrl,
        @Size(max = 500) String coverImageUrl,
        @Positive Integer capacity,
        @Size(max = 255) String organizerEmail,
        String publisherIdentity
) {
}
