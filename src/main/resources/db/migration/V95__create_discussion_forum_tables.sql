-- Turns "Discussions" from a plain filtered view of the Feed into a real forum: a curated topic per
-- discussion (com.nukkad.feed.entity.Post.Topic — a plain @Enumerated(STRING) column like posts.type
-- already is, so unlike audit_logs.action/notifications.type below this needs NO migration when a
-- topic is ever added), a view count, an upvote/downvote score, a "follow this discussion" toggle,
-- and per-reply likes. Everything here is additive to the existing posts/post_comments tables and the
-- Feed module's own tables (post_likes, post_saves, post_hashtags) are untouched.
--
-- post_views mirrors startup_profile_views exactly (see StartupService#recordProfileView): one row per
-- page view, not deduplicated, viewer_id nullable for an anonymous viewer.
-- post_votes mirrors post_likes' shape but carries a signed value so a discussion has a net score
-- instead of a plain like count.
-- post_follows mirrors startup_follows' composite-key toggle shape exactly.
-- post_comment_likes mirrors post_likes' shape, one per reply instead of one per post.

ALTER TABLE posts ADD COLUMN topic VARCHAR(32) NULL;

CREATE TABLE post_views (
    id CHAR(36) NOT NULL,
    post_id CHAR(36) NOT NULL,
    viewer_id CHAR(36) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_post_views_post (post_id),
    CONSTRAINT fk_pview_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_pview_viewer FOREIGN KEY (viewer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE post_votes (
    post_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    value TINYINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_pvote_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_pvote_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_pvote_value CHECK (value IN (-1, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE post_follows (
    user_id CHAR(36) NOT NULL,
    post_id CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, post_id),
    KEY idx_pfollow_post (post_id),
    CONSTRAINT fk_pfollow_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_pfollow_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE post_comment_likes (
    id CHAR(36) NOT NULL,
    comment_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_post_comment_likes_pair (comment_id, user_id),
    CONSTRAINT fk_pclike_comment FOREIGN KEY (comment_id) REFERENCES post_comments(id) ON DELETE CASCADE,
    CONSTRAINT fk_pclike_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE post_comments ADD COLUMN likes_count INT NOT NULL DEFAULT 0;
