package com.nukkad.notification.service;

import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.mapper.NotificationMapper;
import com.nukkad.notification.repository.NotificationRepository;
import com.nukkad.user.repository.MutedAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Old chat-message ("reply") notification rows stay in the table but must not show in the list or badge. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceListTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private NotificationPreferenceService preferenceService;
    @Mock private MutedAccountRepository mutedAccountRepository;

    private NotificationService service() {
        return new NotificationService(notificationRepository, notificationMapper, preferenceService, mutedAccountRepository);
    }

    @Test
    void theListExcludesLegacyChatMessageNotifications() {
        when(notificationRepository.findByUserIdAndTypeNotOrderByCreatedAtDesc(eq("u1"), eq(NotificationType.reply), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().list("u1", 0, 20);

        verify(notificationRepository).findByUserIdAndTypeNotOrderByCreatedAtDesc(eq("u1"), eq(NotificationType.reply), any(Pageable.class));
    }

    @Test
    void theUnreadCountExcludesLegacyChatMessageNotifications() {
        when(notificationRepository.countByUserIdAndIsReadFalseAndTypeNot("u1", NotificationType.reply)).thenReturn(3L);

        assertThat(service().unreadCount("u1")).isEqualTo(3L);
    }
}
