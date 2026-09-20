package com.nukkad.notification.service;

import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.notification.dto.NotificationDto;
import com.nukkad.notification.entity.Notification;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.mapper.NotificationMapper;
import com.nukkad.notification.repository.NotificationRepository;
import com.nukkad.user.repository.MutedAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-side is called across every module as the side effect of an action (idea
 * interest, connection, message, ...). Read-side (list/unread-count/mark-read) is here too.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationPreferenceService preferenceService;
    private final MutedAccountRepository mutedAccountRepository;

    public NotificationService(NotificationRepository notificationRepository, NotificationMapper notificationMapper,
                                NotificationPreferenceService preferenceService, MutedAccountRepository mutedAccountRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.preferenceService = preferenceService;
        this.mutedAccountRepository = mutedAccountRepository;
    }

    public void notify(String recipientUserId, NotificationType type, String title, String message, String relatedId, String actorUserId) {
        if (recipientUserId == null) return;
        if (!preferenceService.isEnabled(recipientUserId, type)) return;
        if (actorUserId != null && mutedAccountRepository.existsByMuterIdAndMutedId(recipientUserId, actorUserId)) return;
        Notification notification = Notification.builder()
                .userId(recipientUserId)
                .type(type)
                .title(title)
                .message(message)
                .relatedId(relatedId)
                .actorUserId(actorUserId)
                .build();
        notificationRepository.save(notification);
    }

    /** Title of the notification a connection request creates — also how it is found again to be cleaned up. */
    public static final String CONNECTION_REQUEST_TITLE = "New connection request";

    /**
     * A connection request notification only means something while the request is still pending. When
     * the request is cancelled or declined it is removed, and a re-sent request removes the older one
     * first, so the recipient never sees a stale or duplicate "wants to connect" entry (with Accept /
     * Decline buttons for a request that no longer exists).
     */
    @Transactional
    public void withdrawConnectionRequest(String recipientUserId, String requesterUserId) {
        notificationRepository.deleteByRecipientAndActorAndTitle(
                recipientUserId, requesterUserId, NotificationType.connection, CONNECTION_REQUEST_TITLE);
    }

    /** The request was accepted: it is answered, so it should stop showing as unread. */
    @Transactional
    public void resolveConnectionRequest(String recipientUserId, String requesterUserId) {
        notificationRepository.markReadByRecipientAndActorAndTitle(
                recipientUserId, requesterUserId, NotificationType.connection, CONNECTION_REQUEST_TITLE);
    }

    /**
     * Chat messages no longer create notifications (they arrive as a short-lived in-app toast and as
     * unread conversations), but every message sent before that change left a {@code reply} row behind.
     * Those old rows are hidden from the notification list and its unread count rather than deleted.
     */
    private static final NotificationType LEGACY_CHAT_MESSAGE_TYPE = NotificationType.reply;

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByUserIdAndTypeNotOrderByCreatedAtDesc(userId, LEGACY_CHAT_MESSAGE_TYPE, pageable)
                .map(notificationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalseAndTypeNot(userId, LEGACY_CHAT_MESSAGE_TYPE);
    }

    @Transactional
    public NotificationDto markRead(String userId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new ForbiddenException("You cannot modify another user's notification");
        }
        notification.setRead(true);
        return notificationMapper.toDto(notificationRepository.saveAndFlush(notification));
    }

    @Transactional
    public void markAllRead(String userId) {
        notificationRepository.markAllAsRead(userId);
    }
}
