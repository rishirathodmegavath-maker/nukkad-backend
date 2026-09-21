package com.nukkad.feed.repository;

import com.nukkad.feed.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, String> {
    // Unfiltered — admin-only use (sees removed posts too).
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Post> findByAuthorIdOrderByCreatedAtDesc(String authorId, Pageable pageable);

    // Admin-only listing of everything still live (ignores visibility: an admin sees every post).
    Page<Post> findByRemovedByAdminFalseOrderByCreatedAtDesc(Pageable pageable);

    /**
     * The member feed: posts the viewer may read (see {@link PostVisibilityQuery}), minus anything an admin has
     * taken down, newest first. {@code authorId} and {@code type} each narrow the list when non-null. The id is a
     * second sort key because created_at is second-precision, so two posts in the same second still page stably.
     */
    @Query("select p from Post p where p.removedByAdmin = false "
            + "and (:authorId is null or p.authorId = :authorId) "
            + "and (:type is null or p.type = :type) "
            + "and " + PostVisibilityQuery.VISIBLE_TO_VIEWER + " "
            + "order by p.createdAt desc, p.id desc")
    Page<Post> findVisibleTo(@Param("viewerId") String viewerId, @Param("authorId") String authorId,
                             @Param("type") Post.Type type, Pageable pageable);

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
}
