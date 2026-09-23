package com.nukkad.messaging.dto;

import jakarta.validation.constraints.NotBlank;

/** What {@code POST /conversations/{id}/attachments} returns, and what {@link SendMessageRequest} takes
 * back — a private object KEY, never a URL (unlike feed's {@code AttachmentRef}, deliberately not reused
 * here: a chat attachment has no permanent public URL to hand back at upload time). */
public record ConversationAttachmentRef(@NotBlank String key, @NotBlank String kind, String fileName) {
}
