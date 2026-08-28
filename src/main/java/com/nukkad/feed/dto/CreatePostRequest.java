package com.nukkad.feed.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePostRequest(@Size(max = 4000) String content, String type, String relatedId, List<AttachmentRef> attachments) {
}
