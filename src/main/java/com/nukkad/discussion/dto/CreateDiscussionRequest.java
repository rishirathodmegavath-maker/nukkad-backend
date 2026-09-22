package com.nukkad.discussion.dto;

import com.nukkad.feed.dto.AttachmentRef;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code topic} is optional (null shows as "General"); everything else mirrors CreatePostRequest. */
public record CreateDiscussionRequest(@Size(max = 4000) String content, String topic, List<AttachmentRef> attachments,
                                       String visibility, @Size(max = 500) String linkUrl) {
}
