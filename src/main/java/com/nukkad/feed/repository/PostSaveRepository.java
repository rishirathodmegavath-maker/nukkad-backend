package com.nukkad.feed.repository;

import com.nukkad.feed.entity.PostSave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface PostSaveRepository extends JpaRepository<PostSave, String> {
    Optional<PostSave> findByPostIdAndUserId(String postId, String userId);

    @Query("select s.postId from PostSave s where s.userId = :userId and s.postId in :postIds")
    Set<String> findSavedPostIds(@Param("userId") String userId, @Param("postIds") Collection<String> postIds);

    /**
     * A bulk delete-by-criteria, unlike delete(entity), doesn't check "was exactly one row
     * affected" — so it can't throw when a concurrent unsave races this one. Naturally idempotent.
     */
    @Modifying
    @Query("DELETE FROM PostSave s WHERE s.postId = :postId AND s.userId = :userId")
    void deleteByPostIdAndUserId(@Param("postId") String postId, @Param("userId") String userId);
}
