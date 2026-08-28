package com.nukkad.messaging.dto;

import com.nukkad.feed.dto.PostDto;

import java.time.Instant;

public record MessageDto(String id, String conversationId, String senderId, String type, String content,
                          String sharedPostId, PostDto sharedPost, boolean isRead, Instant createdAt) {
}
