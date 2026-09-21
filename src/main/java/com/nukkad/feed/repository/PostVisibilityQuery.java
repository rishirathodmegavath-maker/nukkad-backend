package com.nukkad.feed.repository;

/**
 * The one JPQL condition that decides whether a post may be read by a viewer: it is public, the viewer wrote it,
 * or it is connections-only and the viewer is an accepted connection of its author. Every query that lists posts
 * for a member appends this (bind the viewer as {@code :viewerId}) so the rule can't drift between the feed,
 * an author's page and the saved-posts tab. Single-post reads use {@code FeedService#canView}, the same rule.
 */
final class PostVisibilityQuery {

    /** Alias {@code p} must be the Post being filtered. */
    static final String VISIBLE_TO_VIEWER =
            "(p.visibility = com.nukkad.feed.entity.Post.Visibility.PUBLIC or p.authorId = :viewerId "
                    + "or exists (select c.id from Connection c "
                    + "where c.status = com.nukkad.user.entity.Connection.Status.ACCEPTED "
                    + "and ((c.userAId = p.authorId and c.userBId = :viewerId) "
                    + "or (c.userBId = p.authorId and c.userAId = :viewerId))))";

    private PostVisibilityQuery() {
    }
}
