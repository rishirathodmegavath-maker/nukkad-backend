package com.nukkad.notification.repository;

import com.nukkad.notification.entity.Notification;
import com.nukkad.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    Page<Notification> findByUserIdAndTypeNotOrderByCreatedAtDesc(String userId, NotificationType excludedType, Pageable pageable);
    long countByUserIdAndIsReadFalseAndTypeNot(String userId, NotificationType excludedType);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(String userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId AND n.actorUserId = :actorUserId"
            + " AND n.type = :type AND n.title = :title")
    int deleteByRecipientAndActorAndTitle(@Param("userId") String userId, @Param("actorUserId") String actorUserId,
                                          @Param("type") NotificationType type, @Param("title") String title);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.actorUserId = :actorUserId"
            + " AND n.type = :type AND n.title = :title AND n.isRead = false")
    int markReadByRecipientAndActorAndTitle(@Param("userId") String userId, @Param("actorUserId") String actorUserId,
                                            @Param("type") NotificationType type, @Param("title") String title);
}
