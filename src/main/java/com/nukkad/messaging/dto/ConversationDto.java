package com.nukkad.messaging.dto;

import java.time.Instant;

public record ConversationDto(String id, String otherUserId, MessageDto lastMessage, long unreadCount, Instant updatedAt,
                               boolean muted, String nickname, boolean blocked) {
}
