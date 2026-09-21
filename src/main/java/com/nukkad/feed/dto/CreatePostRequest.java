package com.nukkad.feed.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code visibility} is PUBLIC (default) or CONNECTIONS; {@code linkUrl} is an optional http(s) link. */
public record CreatePostRequest(@Size(max = 4000) String content, String type, String relatedId, List<AttachmentRef> attachments,
                                String visibility, @Size(max = 500) String linkUrl) {
}
