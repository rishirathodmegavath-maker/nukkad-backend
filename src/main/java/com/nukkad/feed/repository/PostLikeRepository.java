package com.nukkad.feed.repository;

import com.nukkad.feed.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface PostLikeRepository extends JpaRepository<PostLike, String> {
    Optional<PostLike> findByPostIdAndUserId(String postId, String userId);

    @Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
    Set<String> findLikedPostIds(@Param("userId") String userId, @Param("postIds") Collection<String> postIds);

    /**
     * A bulk delete-by-criteria, unlike delete(entity), doesn't check "was exactly one row
     * affected" — so it can't throw when a concurrent unlike (from the same user's other tab/
     * request racing this one) already removed the row. Naturally idempotent under a toggle race.
     */
    @Modifying
    @Query("DELETE FROM PostLike l WHERE l.postId = :postId AND l.userId = :userId")
    void deleteByPostIdAndUserId(@Param("postId") String postId, @Param("userId") String userId);
}
