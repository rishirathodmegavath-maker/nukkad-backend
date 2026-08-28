package com.nukkad.feed.repository;

import com.nukkad.feed.entity.PostSave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface PostSaveRepository extends JpaRepository<PostSave, String> {
    Optional<PostSave> findByPostIdAndUserId(String postId, String userId);

    @Query("select s.postId from PostSave s where s.userId = :userId and s.postId in :postIds")
    Set<String> findSavedPostIds(@Param("userId") String userId, @Param("postIds") Collection<String> postIds);
}
