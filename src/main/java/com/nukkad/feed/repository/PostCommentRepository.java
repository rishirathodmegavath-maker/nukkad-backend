package com.nukkad.feed.repository;

import com.nukkad.feed.entity.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentRepository extends JpaRepository<PostComment, String> {
    Page<PostComment> findByPostIdOrderByCreatedAtAsc(String postId, Pageable pageable);
}
