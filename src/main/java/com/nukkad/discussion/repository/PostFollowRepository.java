package com.nukkad.discussion.repository;

import com.nukkad.discussion.entity.PostFollow;
import com.nukkad.discussion.entity.PostFollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface PostFollowRepository extends JpaRepository<PostFollow, PostFollowId> {
    boolean existsByUserIdAndPostId(String userId, String postId);

    @Modifying
    @Query("DELETE FROM PostFollow f WHERE f.userId = :userId AND f.postId = :postId")
    void deleteByUserIdAndPostId(@Param("userId") String userId, @Param("postId") String postId);

    long countByPostId(String postId);

    /** Which of a page of discussions this viewer follows, in one query. */
    @Query("select f.postId from PostFollow f where f.userId = :userId and f.postId in :postIds")
    Set<String> findFollowedPostIds(@Param("userId") String userId, @Param("postIds") Collection<String> postIds);

    /** The discussions a member follows, newest-followed first — backs the "Following" tab. */
    @Query("select f.postId from PostFollow f where f.userId = :userId order by f.createdAt desc")
    java.util.List<String> findPostIdsByUserId(@Param("userId") String userId);
}
