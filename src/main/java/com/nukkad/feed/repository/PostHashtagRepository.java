package com.nukkad.feed.repository;

import com.nukkad.feed.entity.PostHashtag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, String> {

    /** A tag and how many posts used it, as returned by {@link #findTrending}. */
    interface TagCount {
        String getTag();

        long getPostCount();
    }

    /**
     * The most-used tags among posts written since {@code since}, counting only posts the viewer may read (so a
     * connections-only post can't leak its tags or inflate a count), most used first and alphabetical among equals.
     * The caller sets the page size to the number of topics wanted.
     */
    @Query("select h.tag as tag, count(h.id) as postCount from PostHashtag h, Post p "
            + "where p.id = h.postId and p.removedByAdmin = false and p.createdAt >= :since "
            + "and " + PostVisibilityQuery.VISIBLE_TO_VIEWER + " "
            + "group by h.tag order by count(h.id) desc, h.tag asc")
    List<TagCount> findTrending(@Param("viewerId") String viewerId, @Param("since") Instant since, Pageable pageable);

    /** Drops a post's tags before they are rewritten after an edit. */
    @Modifying(flushAutomatically = true)
    @Query("delete from PostHashtag h where h.postId = :postId")
    void deleteByPostId(@Param("postId") String postId);

    boolean existsBy();
}
