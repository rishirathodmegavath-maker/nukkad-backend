package com.nukkad.discussion.repository;

import com.nukkad.discussion.entity.PostCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface PostCommentLikeRepository extends JpaRepository<PostCommentLike, String> {
    Optional<PostCommentLike> findByCommentIdAndUserId(String commentId, String userId);

    @Modifying
    @Query("DELETE FROM PostCommentLike l WHERE l.commentId = :commentId AND l.userId = :userId")
    void deleteByCommentIdAndUserId(@Param("commentId") String commentId, @Param("userId") String userId);

    @Query("select l.commentId from PostCommentLike l where l.userId = :userId and l.commentId in :commentIds")
    Set<String> findLikedCommentIds(@Param("userId") String userId, @Param("commentIds") Collection<String> commentIds);
}
