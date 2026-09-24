package com.nukkad.feed.repository;

import com.nukkad.feed.entity.PostInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface PostInteractionRepository extends JpaRepository<PostInteraction, String> {

    /** Post ids among {@code postIds} this user has interacted with (any type) since {@code since} —
     *  batched for a whole candidate pool at once, feeding the personalized feed's recent-interaction
     *  penalty (a soft demotion of a specific already-seen post, never a topic-level exclusion). */
    @Query("select i.postId from PostInteraction i where i.userId = :userId and i.postId in :postIds "
            + "and i.createdAt >= :since")
    Set<String> findRecentlyInteractedPostIds(@Param("userId") String userId, @Param("postIds") Collection<String> postIds,
                                               @Param("since") Instant since);

    /** How many times this viewer has positively engaged (LIKE/SAVE/COMMENT/SHARE) with each of a
     *  set of authors — one batched query for a whole candidate pool, feeding creatorAffinity. */
    @Query("select p.authorId, count(i) from PostInteraction i join Post p on p.id = i.postId "
            + "where i.userId = :userId and p.authorId in :authorIds "
            + "and i.interactionType in (com.nukkad.feed.entity.PostInteraction.Type.LIKE, "
            + "com.nukkad.feed.entity.PostInteraction.Type.SAVE, com.nukkad.feed.entity.PostInteraction.Type.COMMENT, "
            + "com.nukkad.feed.entity.PostInteraction.Type.SHARE) "
            + "group by p.authorId")
    List<Object[]> countPositiveInteractionsByAuthor(@Param("userId") String userId,
                                                       @Param("authorIds") Collection<String> authorIds);
}
