package com.nukkad.feed.repository;

import com.nukkad.feed.entity.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, String> {
    Page<PostComment> findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(String postId, Pageable pageable);

    Page<PostComment> findByParentCommentIdOrderByCreatedAtAsc(String parentCommentId, Pageable pageable);

    long countByParentCommentId(String parentCommentId);

    @Query("select c.parentCommentId, count(c) from PostComment c where c.parentCommentId in :parentIds group by c.parentCommentId")
    List<Object[]> countRepliesGroupedByParent(@Param("parentIds") Collection<String> parentIds);

    // ---- Discussions: reply likes and participant counting ----

    /** Same atomic-UPDATE reasoning as {@code PostRepository#incrementLikesCount}. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostComment c SET c.likesCount = c.likesCount + 1 WHERE c.id = :id")
    void incrementLikesCount(@Param("id") String id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostComment c SET c.likesCount = GREATEST(c.likesCount - 1, 0) WHERE c.id = :id")
    void decrementLikesCount(@Param("id") String id);

    /** Everyone who has replied to one discussion — the other half of its "participants" count
     *  (unioned with the discussion's own author in {@code DiscussionService}). */
    @Query("select distinct c.authorId from PostComment c where c.postId = :postId")
    List<String> findDistinctAuthorIdsByPostId(@Param("postId") String postId);

    /** Same, batched for a whole list page: rows of (postId, authorId) — de-duplicated per post in Java. */
    @Query("select c.postId, c.authorId from PostComment c where c.postId in :postIds")
    List<Object[]> findAuthorIdsByPostIds(@Param("postIds") Collection<String> postIds);

    /** The reply-side half of the platform-wide "participants" stat (see PostRepository#findDistinctDiscussionAuthorIds
     *  for the other half) — only replies on a public, live discussion count. */
    @Query("select distinct c.authorId from PostComment c where c.postId in "
            + "(select p.id from Post p where p.type = com.nukkad.feed.entity.Post.Type.discussion "
            + "and p.removedByAdmin = false and p.visibility = com.nukkad.feed.entity.Post.Visibility.PUBLIC)")
    List<String> findDistinctCommenterIdsOnDiscussions();

    /** The most recent reply time for a page of discussions, batched: rows of (postId, latest createdAt).
     *  A discussion with no replies has no row — the caller falls back to the post's own createdAt. */
    @Query("select c.postId, max(c.createdAt) from PostComment c where c.postId in :postIds group by c.postId")
    List<Object[]> findLatestCommentTimeByPostIds(@Param("postIds") Collection<String> postIds);

    /** Total replies across every public, live discussion — the "Replies" platform stat. */
    @Query("select count(c) from PostComment c where c.postId in "
            + "(select p.id from Post p where p.type = com.nukkad.feed.entity.Post.Type.discussion "
            + "and p.removedByAdmin = false and p.visibility = com.nukkad.feed.entity.Post.Visibility.PUBLIC)")
    long countCommentsOnDiscussions();
}
