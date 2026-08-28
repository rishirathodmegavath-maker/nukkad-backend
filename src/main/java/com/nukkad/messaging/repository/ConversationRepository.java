package com.nukkad.messaging.repository;

import com.nukkad.messaging.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {
    Optional<Conversation> findByUserAIdAndUserBId(String userAId, String userBId);

    /** Excludes conversations the viewer soft-deleted, unless a newer message has since bumped updatedAt. */
    @Query("select c from Conversation c where "
            + "((c.userAId = :viewerId and (c.deletedAtByUserA is null or c.updatedAt > c.deletedAtByUserA)) "
            + "or (c.userBId = :viewerId and (c.deletedAtByUserB is null or c.updatedAt > c.deletedAtByUserB))) "
            + "order by c.updatedAt desc")
    Page<Conversation> findVisibleForViewer(@Param("viewerId") String viewerId, Pageable pageable);

    /**
     * Unconditionally bumps updatedAt via a direct UPDATE. A plain save() after a new message is a
     * no-op under Hibernate dirty-checking (no field on the loaded Conversation actually changed),
     * so the @UpdateTimestamp column silently never advances — this bypasses that entirely.
     */
    @Modifying
    @Query("update Conversation c set c.updatedAt = :now where c.id = :id")
    void touchUpdatedAt(@Param("id") String id, @Param("now") Instant now);
}
