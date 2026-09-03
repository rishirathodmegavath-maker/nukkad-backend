package com.nukkad.messaging.repository;

import com.nukkad.messaging.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, String> {
    List<ConversationParticipant> findByConversationIdAndDeletedAtIsNull(String conversationId);

    Optional<ConversationParticipant> findByConversationIdAndUserId(String conversationId, String userId);

    Optional<ConversationParticipant> findByConversationIdAndUserIdAndDeletedAtIsNull(String conversationId, String userId);

    boolean existsByConversationIdAndUserIdAndDeletedAtIsNull(String conversationId, String userId);

    long countByConversationIdAndDeletedAtIsNull(String conversationId);

    long countByConversationIdAndRoleAndDeletedAtIsNull(String conversationId, ConversationParticipant.Role role);

    @Query("select p.conversationId from ConversationParticipant p where p.userId = :userId and p.deletedAt is null")
    List<String> findVisibleGroupConversationIdsForUser(@Param("userId") String userId);
}
