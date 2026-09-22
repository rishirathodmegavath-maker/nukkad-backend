package com.nukkad.discussion.repository;

import com.nukkad.discussion.entity.PostView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostViewRepository extends JpaRepository<PostView, String> {
    long countByPostId(String postId);

    /** View counts for a page of discussions in one query — a discussion nobody has viewed has no row. */
    @Query("select v.postId, count(v) from PostView v where v.postId in :ids group by v.postId")
    List<Object[]> countByPostIds(@Param("ids") Collection<String> ids);
}
