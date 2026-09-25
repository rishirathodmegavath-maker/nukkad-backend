package com.nukkad.admin.dto;

import com.nukkad.feed.dto.AttachmentRef;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * An admin publishing a Feed post from the admin panel. Same fields as a member's own post (see
 * CreatePostRequest). {@code authorEmail} is optional: with it, that member becomes the author and the post
 * appears as theirs; without it, the admin's own account is the author and the post is a platform post.
 * {@code publisherIdentity} and {@code platformEngagementCount} only apply to a platform post — see
 * FeedService#createAsAdmin. {@code publisherIdentity} must name one of {@link com.nukkad.feed.entity.Post.PublisherIdentity}'s
 * constants (case-insensitive) or is rejected; blank/omitted falls back to the enum's first constant.
 */
public record AdminCreatePostRequest(
        @Size(max = 4000) String content,
        String type,
        String relatedId,
        List<AttachmentRef> attachments,
        String visibility,
        @Size(max = 500) String linkUrl,
        @Size(max = 255) String authorEmail,
        String publisherIdentity,
        @Min(0) Integer platformEngagementCount
) {
}
