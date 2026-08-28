package com.nukkad.feed.repository;

import com.nukkad.feed.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, String> {
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Post> findByAuthorIdOrderByCreatedAtDesc(String authorId, Pageable pageable);

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
}
