package com.nukkad.feed.repository;

import com.nukkad.feed.entity.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
