package com.nukkad.feed.repository;

import com.nukkad.feed.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, String> {
    // Unfiltered — admin-only use (sees removed posts too).
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Post> findByAuthorIdOrderByCreatedAtDesc(String authorId, Pageable pageable);

    /** Posts whose text contains {@code fragment}: used once to backfill hashtags for posts that predate them. */
    Page<Post> findByContentContaining(String fragment, Pageable pageable);

    // Admin-only listing of everything still live (ignores visibility: an admin sees every post).
    Page<Post> findByRemovedByAdminFalseOrderByCreatedAtDesc(Pageable pageable);

    /**
     * The member feed: posts the viewer may read (see {@link PostVisibilityQuery}), minus anything an admin has
     * taken down, newest first. {@code authorId}, {@code type}, {@code tag} (a lowercase hashtag) and
     * {@code chapterId} (a post's author's own chapter at the time of posting — see {@code Post#chapterId})
     * each narrow the list when non-null. The id is a second sort key because created_at is second-precision,
     * so two posts in the same second still page stably.
     */
    @Query("select p from Post p where p.removedByAdmin = false "
            + "and (:authorId is null or p.authorId = :authorId) "
            + "and (:type is null or p.type = :type) "
            + "and (:tag is null or exists (select h.id from PostHashtag h where h.postId = p.id and h.tag = :tag)) "
            + "and (:chapterId is null or p.chapterId = :chapterId) "
            + "and " + PostVisibilityQuery.VISIBLE_TO_VIEWER + " "
            + "order by p.createdAt desc, p.id desc")
    Page<Post> findVisibleTo(@Param("viewerId") String viewerId, @Param("authorId") String authorId,
                             @Param("type") Post.Type type, @Param("tag") String tag,
                             @Param("chapterId") String chapterId, Pageable pageable);

    /**
     * Atomic single-statement counter updates. A read-modify-write via the loaded entity
     * (load count, increment in Java, save) let concurrent requests on the same post deadlock
     * under MySQL — two transactions each holding one row's lock while waiting on the other's.
     * An in-place UPDATE takes the row lock once and releases it immediately, so it can't deadlock.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.commentsCount = p.commentsCount + 1 WHERE p.id = :id")
    void incrementCommentsCount(@Param("id") String id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likesCount = p.likesCount + 1 WHERE p.id = :id")
    void incrementLikesCount(@Param("id") String id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likesCount = GREATEST(p.likesCount - 1, 0) WHERE p.id = :id")
    void decrementLikesCount(@Param("id") String id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.commentsCount = GREATEST(p.commentsCount - :by, 0) WHERE p.id = :id")
    void decrementCommentsCount(@Param("id") String id, @Param("by") int by);

    // ---- Discussions ----

    /**
     * One flexible query behind every Discussions list tab: Recent (no extra filter), Unanswered
     * ({@code unansweredOnly=true}), My Discussions ({@code authorId=viewerId}) and the topic/tag
     * filters, all composable. "Following" and "Trending" are handled separately below since they
     * need an id-list join / an in-memory score respectively.
     */
    @Query("select p from Post p where p.removedByAdmin = false and p.type = com.nukkad.feed.entity.Post.Type.discussion "
            + "and (:authorId is null or p.authorId = :authorId) "
            + "and (:topic is null or p.topic = :topic) "
            + "and (:tag is null or exists (select h.id from PostHashtag h where h.postId = p.id and h.tag = :tag)) "
            + "and (:unansweredOnly = false or p.commentsCount = 0) "
            + "and " + PostVisibilityQuery.VISIBLE_TO_VIEWER + " "
            + "order by p.createdAt desc, p.id desc")
    Page<Post> findDiscussions(@Param("viewerId") String viewerId, @Param("authorId") String authorId,
                                @Param("topic") Post.Topic topic, @Param("tag") String tag,
                                @Param("unansweredOnly") boolean unansweredOnly, Pageable pageable);

    /** Backs the "Following" tab: discussions the viewer follows (see PostFollowRepository), same topic/tag filters. */
    @Query("select p from Post p where p.removedByAdmin = false and p.type = com.nukkad.feed.entity.Post.Type.discussion "
            + "and p.id in :ids "
            + "and (:topic is null or p.topic = :topic) "
            + "and (:tag is null or exists (select h.id from PostHashtag h where h.postId = p.id and h.tag = :tag)) "
            + "and " + PostVisibilityQuery.VISIBLE_TO_VIEWER + " "
            + "order by p.createdAt desc, p.id desc")
    Page<Post> findDiscussionsByIds(@Param("viewerId") String viewerId, @Param("ids") Collection<String> ids,
                                     @Param("topic") Post.Topic topic, @Param("tag") String tag, Pageable pageable);

    /**
     * A bounded, recency-capped pool for the "Trending" tab: {@link com.nukkad.discussion.service.DiscussionService}
     * scores this pool by real recent votes + replies and sorts/pages it in memory, rather than expressing that
     * score as JPQL (which would need a correlated aggregate subquery per row). {@code pageable} caps the pool
     * size (e.g. the 200 most recent), not the final page — trending is never a pure recency query.
     */
    @Query("select p from Post p where p.removedByAdmin = false and p.type = com.nukkad.feed.entity.Post.Type.discussion "
            + "and p.createdAt >= :since "
            + "and (:topic is null or p.topic = :topic) "
            + "and (:tag is null or exists (select h.id from PostHashtag h where h.postId = p.id and h.tag = :tag)) "
            + "and " + PostVisibilityQuery.VISIBLE_TO_VIEWER + " "
            + "order by p.createdAt desc")
    List<Post> findRecentDiscussionsForTrending(@Param("viewerId") String viewerId, @Param("since") Instant since,
                                                 @Param("topic") Post.Topic topic, @Param("tag") String tag, Pageable pageable);

    /** Other discussions worth surfacing next to this one: same curated topic, or sharing at least one hashtag.
     *  {@code tags} must never be empty — pass a sentinel value that can't match when there are none. */
    @Query("select p from Post p where p.removedByAdmin = false and p.type = com.nukkad.feed.entity.Post.Type.discussion "
            + "and p.id <> :excludeId "
            + "and ((:topic is not null and p.topic = :topic) "
            + "or exists (select h.id from PostHashtag h where h.postId = p.id and h.tag in :tags)) "
            + "and " + PostVisibilityQuery.VISIBLE_TO_VIEWER + " "
            + "order by p.createdAt desc")
    List<Post> findRelatedDiscussions(@Param("viewerId") String viewerId, @Param("excludeId") String excludeId,
                                       @Param("topic") Post.Topic topic, @Param("tags") Collection<String> tags, Pageable pageable);

    /** Real, all-time counts for "Popular Topics" — public discussions only, so a connections-only
     *  discussion never inflates a count anyone can see. A topic nobody has used yet has no row. */
    @Query("select p.topic, count(p) from Post p where p.type = com.nukkad.feed.entity.Post.Type.discussion "
            + "and p.removedByAdmin = false and p.visibility = com.nukkad.feed.entity.Post.Visibility.PUBLIC "
            + "group by p.topic")
    List<Object[]> countDiscussionsByTopic();

    long countByTypeAndRemovedByAdminFalseAndVisibility(Post.Type type, Post.Visibility visibility);

    // ---- Personalized feed candidate pool (com.nukkad.feed.recommendation.PostCandidatePoolService) ----
    // Every query here folds in PostVisibilityQuery.VISIBLE_TO_VIEWER and excludes already-shown/
    // hidden posts via one merged NOT IN parameter. excludeIds must never be an empty collection —
    // pass a single sentinel value that can't match a real id when there's nothing to exclude
    // (same technique DiscussionService already uses for its own "no such tag" sentinel).

    /** Recent posts from a specific set of authors (followed creators + accepted connections) —
     *  one of three candidate buckets. {@code authorIds} must never be empty either, for the same
     *  reason as {@code excludeIds}. */
    @Query("select p from Post p where p.removedByAdmin = false "
            + "and p.authorId in :authorIds and p.createdAt >= :since and p.id not in :excludeIds "
            + "and " + PostVisibilityQuery.VISIBLE_TO_VIEWER + " "
            + "order by p.createdAt desc")
    List<Post> findRecentByAuthors(@Param("viewerId") String viewerId, @Param("authorIds") Collection<String> authorIds,
                                    @Param("since") Instant since, @Param("excludeIds") Collection<String> excludeIds,
                                    Pageable pageable);

    /** Recent posts carrying at least one of a set of hashtags — the affinity-matched bucket. */
    @Query("select p from Post p where p.removedByAdmin = false "
            + "and exists (select h.id from PostHashtag h where h.postId = p.id and h.tag in :tags) "
            + "and p.createdAt >= :since and p.id not in :excludeIds "
            + "and " + PostVisibilityQuery.VISIBLE_TO_VIEWER + " "
            + "order by p.createdAt desc")
    List<Post> findRecentByTags(@Param("viewerId") String viewerId, @Param("tags") Collection<String> tags,
                                 @Param("since") Instant since, @Param("excludeIds") Collection<String> excludeIds,
                                 Pageable pageable);

    /** Recent posts with no author/tag filter — the trending/general bucket, and (called again
     *  with {@code since} pushed back to the beginning of time) the cold-start/thin-pool fallback. */
    @Query("select p from Post p where p.removedByAdmin = false "
            + "and p.createdAt >= :since and p.id not in :excludeIds "
            + "and " + PostVisibilityQuery.VISIBLE_TO_VIEWER + " "
            + "order by p.createdAt desc")
    List<Post> findRecentGeneral(@Param("viewerId") String viewerId, @Param("since") Instant since,
                                  @Param("excludeIds") Collection<String> excludeIds, Pageable pageable);

    /** Everyone who has started a public discussion — half of the "participants" platform stat (see
     *  PostCommentRepository for the reply-side half); unioned and de-duplicated in Java. */
    @Query("select distinct p.authorId from Post p where p.type = com.nukkad.feed.entity.Post.Type.discussion "
            + "and p.removedByAdmin = false and p.visibility = com.nukkad.feed.entity.Post.Visibility.PUBLIC")
    List<String> findDistinctDiscussionAuthorIds();
}
