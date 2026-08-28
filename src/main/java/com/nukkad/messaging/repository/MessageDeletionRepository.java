package com.nukkad.messaging.repository;

import com.nukkad.messaging.entity.MessageDeletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface MessageDeletionRepository extends JpaRepository<MessageDeletion, String> {
    /** Of the given message ids, which ones does this user already have a deletion row for. */
    @Query("select md.messageId from MessageDeletion md where md.userId = :userId and md.messageId in :messageIds")
    Set<String> findDeletedMessageIds(@Param("userId") String userId, @Param("messageIds") Collection<String> messageIds);
}
