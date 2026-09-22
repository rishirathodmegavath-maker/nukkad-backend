package com.nukkad.discussion.dto;

import java.time.Instant;

/** {@code CommentDto} (Feed) plus the two fields a plain Feed comment never needed: a like count and
 *  whether this viewer liked it. A separate DTO rather than widening {@code CommentDto} itself, so the
 *  generic Feed comment list/response shape is untouched. */
public record DiscussionCommentDto(String id, String postId, String parentCommentId, String authorId, String content,
                                    int replyCount, int likesCount, boolean isLiked, Instant createdAt) {
}
