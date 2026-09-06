package com.nukkad.feed.repository;

import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.PostSave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Root selection stays PostSave (not Post) so each row still carries its own savedAt — the
    // service batch-fetches the matching Post entities separately (same bulk-by-id pattern already
    // used for likedIds/savedIds above) rather than projecting a join result, keeping this consistent
    // with the rest of the codebase's "plain ID columns, no ORM associations" style instead of adding
    // a first-of-its-kind constructor-expression projection. `p.id = s.postId` is an unmapped ad hoc
    // join (JPA 2.1+) since PostSave has no @ManyToOne to Post; `id` is a secondary sort key so two
    // saves landing in the same second (created_at is second-precision) still paginate deterministically.
    @Query("select s from PostSave s join Post p on p.id = s.postId "
            + "where s.userId = :userId and (:type is null or p.type = :type) "
            + "order by s.createdAt desc, s.id desc")
    Page<PostSave> findByUserOrderBySavedAtDesc(@Param("userId") String userId, @Param("type") Post.Type type, Pageable pageable);

    @Query("select s from PostSave s join Post p on p.id = s.postId "
            + "where s.userId = :userId and (:type is null or p.type = :type) "
            + "order by s.createdAt asc, s.id asc")
    Page<PostSave> findByUserOrderBySavedAtAsc(@Param("userId") String userId, @Param("type") Post.Type type, Pageable pageable);

    @Query("select s from PostSave s join Post p on p.id = s.postId "
            + "where s.userId = :userId and (:type is null or p.type = :type) "
            + "order by p.createdAt desc, p.id desc")
    Page<PostSave> findByUserOrderByPostCreatedAtDesc(@Param("userId") String userId, @Param("type") Post.Type type, Pageable pageable);

    @Query("select s from PostSave s join Post p on p.id = s.postId "
            + "where s.userId = :userId and (:type is null or p.type = :type) "
            + "order by p.createdAt asc, p.id asc")
    Page<PostSave> findByUserOrderByPostCreatedAtAsc(@Param("userId") String userId, @Param("type") Post.Type type, Pageable pageable);

    /**
     * A bulk delete-by-criteria, unlike delete(entity), doesn't check "was exactly one row
     * affected" — so it can't throw when a concurrent unsave races this one. Naturally idempotent.
     */
    @Modifying
    @Query("DELETE FROM PostSave s WHERE s.postId = :postId AND s.userId = :userId")
    void deleteByPostIdAndUserId(@Param("postId") String postId, @Param("userId") String userId);
}
