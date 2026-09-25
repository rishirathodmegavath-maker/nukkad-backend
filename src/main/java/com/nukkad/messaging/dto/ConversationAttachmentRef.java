package com.nukkad.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What {@code POST /conversations/{id}/attachments} returns, and what {@link SendMessageRequest} takes
 * back — a private object KEY, never a URL (unlike feed's {@code AttachmentRef}, deliberately not reused
 * here: a chat attachment has no permanent public URL to hand back at upload time).
 *
 * <p>Everything in here comes back from the client, so none of it is trusted: the server re-derives
 * {@code kind} from the stored object, requires {@code key} to be a key it minted for that same
 * conversation, and sanitizes {@code fileName} (a display label only). The sizes just bound the input. */
public record ConversationAttachmentRef(@NotBlank @Size(max = 500) String key,
                                        @NotBlank @Size(max = 20) String kind,
                                        @Size(max = 255) String fileName) {
}
