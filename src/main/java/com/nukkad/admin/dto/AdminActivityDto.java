package com.nukkad.admin.dto;

import java.time.Instant;

/** One line in the admin activity timeline. {@code label} is a short, non-private description
 *  (a title, name or category) — never message content or application text. */
public record AdminActivityDto(String type, Instant occurredAt, String actorId, String actorName,
                                String label, String targetId) {
}
