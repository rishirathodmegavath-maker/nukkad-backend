package com.nukkad.feed.repository;

import com.nukkad.feed.entity.PostHide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface PostHideRepository extends JpaRepository<PostHide, String> {
    Optional<PostHide> findByPostIdAndUserId(String postId, String userId);

    @Query("select h.postId from PostHide h where h.userId = :userId and h.postId in :postIds")
    Set<String> findHiddenPostIds(@Param("userId") String userId, @Param("postIds") Collection<String> postIds);

    /** Every post this viewer has ever hidden — the personalized feed's candidate pool excludes
     *  all of them up front, not just the ones already loaded on the client. */
    @Query("select h.postId from PostHide h where h.userId = :userId")
    Set<String> findAllPostIdsByUserId(@Param("userId") String userId);

    /** A bulk delete-by-criteria, same idempotent-under-a-race reasoning as PostLikeRepository's. */
    @Modifying
    @Query("DELETE FROM PostHide h WHERE h.postId = :postId AND h.userId = :userId")
    void deleteByPostIdAndUserId(@Param("postId") String postId, @Param("userId") String userId);
}
