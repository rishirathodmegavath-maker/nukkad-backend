package com.nukkad.admin.dto;

import com.nukkad.feed.dto.AttachmentRef;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * An admin publishing a Feed post from the admin panel. Same fields as a member's own post (see
 * CreatePostRequest). {@code authorEmail} is optional: with it, that member becomes the author and the post
 * appears as theirs; without it, the admin's own account is the author.
 */
public record AdminCreatePostRequest(
        @Size(max = 4000) String content,
        String type,
        String relatedId,
        List<AttachmentRef> attachments,
        String visibility,
        @Size(max = 500) String linkUrl,
        @Size(max = 255) String authorEmail
) {
}
