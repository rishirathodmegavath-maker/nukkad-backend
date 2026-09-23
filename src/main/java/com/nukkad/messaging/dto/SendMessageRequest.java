package com.nukkad.messaging.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(@Size(max = 4000) String content, String sharedPostId, String replyToMessageId,
                                  @Valid ConversationAttachmentRef attachment) {
}
