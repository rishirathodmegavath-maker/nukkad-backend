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

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(notificationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
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
