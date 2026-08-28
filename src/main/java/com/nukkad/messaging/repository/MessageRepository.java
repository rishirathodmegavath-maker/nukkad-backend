package com.nukkad.messaging.repository;

import com.nukkad.messaging.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, String> {
    Page<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId, Pageable pageable);
    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(String conversationId);
    long countByConversationIdAndSenderIdNotAndIsReadFalse(String conversationId, String senderId);
    List<Message> findByConversationId(String conversationId);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.conversationId = :conversationId AND m.senderId <> :viewerId AND m.isRead = false")
    int markConversationRead(@Param("conversationId") String conversationId, @Param("viewerId") String viewerId);

    /** Excludes messages the viewer has individually deleted (message_deletions) — the shared rows are untouched. */
    @Query("select m from Message m where m.conversationId = :conversationId "
            + "and not exists (select 1 from MessageDeletion md where md.messageId = m.id and md.userId = :viewerId) "
            + "order by m.createdAt asc")
    Page<Message> findVisibleForViewer(@Param("conversationId") String conversationId, @Param("viewerId") String viewerId, Pageable pageable);

    @Query("select m from Message m where m.conversationId = :conversationId "
            + "and not exists (select 1 from MessageDeletion md where md.messageId = m.id and md.userId = :viewerId) "
            + "order by m.createdAt desc")
    List<Message> findLatestVisibleForViewer(@Param("conversationId") String conversationId, @Param("viewerId") String viewerId, Pageable pageable);

    @Query("select count(m) from Message m where m.conversationId = :conversationId and m.senderId <> :viewerId and m.isRead = false "
            + "and not exists (select 1 from MessageDeletion md where md.messageId = m.id and md.userId = :viewerId)")
    long countUnreadVisibleForViewer(@Param("conversationId") String conversationId, @Param("viewerId") String viewerId);
}
