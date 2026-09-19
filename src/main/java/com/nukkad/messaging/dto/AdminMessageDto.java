package com.nukkad.messaging.dto;

import java.time.Instant;

/** A deliberately narrower view of a message than {@link MessageDto} — for admin evidence review
 *  only (e.g. reviewing a reported conversation), never sent to a normal participant. Omits read
 *  receipts, reply-preview and shared-post resolution, none of which matter for moderation and
 *  which would otherwise require treating the admin as a conversation participant. */
public record AdminMessageDto(
        String id,
        String senderId,
        String messageType,
        String content,
        boolean unsent,
        Instant createdAt
) {
}
