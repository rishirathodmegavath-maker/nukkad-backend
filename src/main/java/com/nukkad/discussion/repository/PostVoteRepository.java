package com.nukkad.discussion.repository;

import com.nukkad.discussion.entity.PostVote;
import com.nukkad.discussion.entity.PostVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostVoteRepository extends JpaRepository<PostVote, PostVoteId> {
    Optional<PostVote> findByPostIdAndUserId(String postId, String userId);

    @Modifying
    @Query("DELETE FROM PostVote v WHERE v.postId = :postId AND v.userId = :userId")
    void deleteByPostIdAndUserId(@Param("postId") String postId, @Param("userId") String userId);

    /** SUM(value) is null (not 0) for a discussion with no votes at all — callers default that to 0. */
    @Query("select coalesce(sum(v.value), 0) from PostVote v where v.postId = :postId")
    int sumValueByPostId(@Param("postId") String postId);

    /** Net scores for a page of discussions in one query: rows of (postId, sum). A discussion with no votes has no row. */
    @Query("select v.postId, sum(v.value) from PostVote v where v.postId in :ids group by v.postId")
    List<Object[]> sumValueByPostIds(@Param("ids") Collection<String> ids);

    /** This viewer's own vote on a page of discussions, batched: rows of (postId, value). No row means no vote. */
    @Query("select v.postId, v.value from PostVote v where v.userId = :userId and v.postId in :ids")
    List<Object[]> findValuesByUserIdAndPostIds(@Param("userId") String userId, @Param("ids") Collection<String> ids);
}
