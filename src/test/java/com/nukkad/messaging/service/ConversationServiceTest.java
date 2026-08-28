package com.nukkad.messaging.service;

import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.feed.service.FeedService;
import com.nukkad.investor.repository.IntroRequestRepository;
import com.nukkad.messaging.dto.MessageDto;
import com.nukkad.messaging.entity.Conversation;
import com.nukkad.messaging.entity.Message;
import com.nukkad.messaging.repository.ConversationRepository;
import com.nukkad.messaging.repository.MessageDeletionRepository;
import com.nukkad.messaging.repository.MessageRepository;
import com.nukkad.opportunity.repository.OpportunityApplicantRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserBlockRepository;
import com.nukkad.user.service.UserPrivacySettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the narrow messaging-permission exception added for accepted opportunity applications:
 * an ACCEPTED application counts as "connected" for {@link UserPrivacySettingsService#canMessage}
 * purposes only — it must not create, read as, or otherwise touch a real {@code Connection} row,
 * and normal connection-based messaging must keep working exactly as before this exception existed.
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private MessageDeletionRepository messageDeletionRepository;
    @Mock private MessageEncryptionService encryptionService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private ConnectionRepository connectionRepository;
    @Mock private OpportunityApplicantRepository opportunityApplicantRepository;
    @Mock private StartupTeamMemberRepository startupTeamMemberRepository;
    @Mock private IntroRequestRepository introRequestRepository;
    @Mock private UserPrivacySettingsService privacySettingsService;
    @Mock private FeedService feedService;

    private ConversationService service() {
        return new ConversationService(conversationRepository, messageRepository, messageDeletionRepository,
                encryptionService, messagingTemplate, userBlockRepository, connectionRepository,
                opportunityApplicantRepository, startupTeamMemberRepository, introRequestRepository,
                privacySettingsService, feedService);
    }

    private Conversation conversation(String senderId, String recipientId) {
        return Conversation.builder().id("conv1").userAId(senderId).userBId(recipientId).build();
    }

    /** Stubs the read-side lookups {@code toDto} needs to build the broadcast DTO sent to the recipient. */
    private void stubConversationDtoLookups(String viewerId, String otherId) {
        when(messageRepository.findLatestVisibleForViewer(eq("conv1"), eq(viewerId), any())).thenReturn(List.of());
        when(messageRepository.countUnreadVisibleForViewer("conv1", viewerId)).thenReturn(0L);
        when(userBlockRepository.existsBetween(viewerId, otherId)).thenReturn(false);
    }

    private void stubMessagePersistenceAndEncryption() {
        when(encryptionService.encrypt(any())).thenReturn("ciphertext");
        when(encryptionService.decrypt(any())).thenReturn("Hello!");
        when(messageRepository.saveAndFlush(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId("msg1");
            m.setCreatedAt(Instant.now());
            return m;
        });
    }

    // ---- 14 & 15. Accepted application unlocks messaging without creating a real Connection ----

    @Test
    void acceptedApplicationUnlocksMessagingWithoutTouchingConnectionRepositoryWrites() {
        Conversation conv = conversation("applicant1", "owner1");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("applicant1", "owner1")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("applicant1", "owner1")).thenReturn(false);
        when(opportunityApplicantRepository.existsAcceptedApplicationBetween("applicant1", "owner1")).thenReturn(true);
        when(privacySettingsService.canMessage("owner1", true)).thenReturn(true);
        stubMessagePersistenceAndEncryption();
        stubConversationDtoLookups("owner1", "applicant1");

        MessageDto dto = service().sendMessage("conv1", "applicant1", "Hello!", null);

        assertThat(dto.content()).isEqualTo("Hello!");
        // The exception is read-only: it never creates, updates, or reads a real Connection row.
        verify(connectionRepository, never()).save(any());
        verify(connectionRepository, never()).saveAndFlush(any());
        verify(connectionRepository, never()).findByUserAIdAndUserBId(any(), any());
    }

    @Test
    void strangerWithoutConnectionOrAcceptedApplicationIsStillBlockedByConnectionsOnlyPrivacy() {
        Conversation conv = conversation("stranger1", "owner1");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("stranger1", "owner1")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("stranger1", "owner1")).thenReturn(false);
        when(opportunityApplicantRepository.existsAcceptedApplicationBetween("stranger1", "owner1")).thenReturn(false);
        when(privacySettingsService.canMessage("owner1", false)).thenReturn(false);

        assertThatThrownBy(() -> service().sendMessage("conv1", "stranger1", "Hi", null))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).saveAndFlush(any());
    }

    // ---- Active startup team membership unlocks messaging the same way ----

    @Test
    void activeStartupTeammateUnlocksMessagingWithoutTouchingConnectionRepositoryWrites() {
        Conversation conv = conversation("teammate1", "founder1");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("teammate1", "founder1")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("teammate1", "founder1")).thenReturn(false);
        when(opportunityApplicantRepository.existsAcceptedApplicationBetween("teammate1", "founder1")).thenReturn(false);
        when(startupTeamMemberRepository.existsActiveTeamMembershipBetween("teammate1", "founder1")).thenReturn(true);
        when(privacySettingsService.canMessage("founder1", true)).thenReturn(true);
        stubMessagePersistenceAndEncryption();
        stubConversationDtoLookups("founder1", "teammate1");

        MessageDto dto = service().sendMessage("conv1", "teammate1", "Hello!", null);

        assertThat(dto.content()).isEqualTo("Hello!");
        verify(connectionRepository, never()).save(any());
        verify(connectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void pendingJoinRequesterDoesNotAutomaticallyGainMessagingAccess() {
        Conversation conv = conversation("requester1", "founder1");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("requester1", "founder1")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("requester1", "founder1")).thenReturn(false);
        when(opportunityApplicantRepository.existsAcceptedApplicationBetween("requester1", "founder1")).thenReturn(false);
        when(startupTeamMemberRepository.existsActiveTeamMembershipBetween("requester1", "founder1")).thenReturn(false);
        when(privacySettingsService.canMessage("founder1", false)).thenReturn(false);

        assertThatThrownBy(() -> service().sendMessage("conv1", "requester1", "Hi", null))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).saveAndFlush(any());
    }

    // ---- Accepted investor/founder introduction unlocks messaging the same way ----

    @Test
    void acceptedIntroRequestUnlocksMessagingWithoutTouchingConnectionRepositoryWrites() {
        Conversation conv = conversation("investor1", "founder1");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("investor1", "founder1")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("investor1", "founder1")).thenReturn(false);
        when(opportunityApplicantRepository.existsAcceptedApplicationBetween("investor1", "founder1")).thenReturn(false);
        when(startupTeamMemberRepository.existsActiveTeamMembershipBetween("investor1", "founder1")).thenReturn(false);
        when(introRequestRepository.existsAcceptedIntroBetween("investor1", "founder1")).thenReturn(true);
        when(privacySettingsService.canMessage("founder1", true)).thenReturn(true);
        stubMessagePersistenceAndEncryption();
        stubConversationDtoLookups("founder1", "investor1");

        MessageDto dto = service().sendMessage("conv1", "investor1", "Hello!", null);

        assertThat(dto.content()).isEqualTo("Hello!");
        verify(connectionRepository, never()).save(any());
        verify(connectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void pendingIntroRequestDoesNotAutomaticallyGainMessagingAccess() {
        Conversation conv = conversation("investor1", "founder1");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("investor1", "founder1")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("investor1", "founder1")).thenReturn(false);
        when(opportunityApplicantRepository.existsAcceptedApplicationBetween("investor1", "founder1")).thenReturn(false);
        when(startupTeamMemberRepository.existsActiveTeamMembershipBetween("investor1", "founder1")).thenReturn(false);
        when(introRequestRepository.existsAcceptedIntroBetween("investor1", "founder1")).thenReturn(false);
        when(privacySettingsService.canMessage("founder1", false)).thenReturn(false);

        assertThatThrownBy(() -> service().sendMessage("conv1", "investor1", "Hi", null))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).saveAndFlush(any());
    }

    // ---- 16. Existing connection-based messaging behavior remains unchanged ----

    @Test
    void realConnectionStillUnlocksMessagingAndShortCircuitsTheApplicationCheck() {
        Conversation conv = conversation("friend1", "owner1");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("friend1", "owner1")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("friend1", "owner1")).thenReturn(true);
        when(privacySettingsService.canMessage("owner1", true)).thenReturn(true);
        stubMessagePersistenceAndEncryption();
        stubConversationDtoLookups("owner1", "friend1");

        MessageDto dto = service().sendMessage("conv1", "friend1", "Hello!", null);

        assertThat(dto.content()).isEqualTo("Hello!");
        // Java's || short-circuits once the real connection check is true — the opportunity-based
        // exception is never even consulted for an already-connected pair.
        verify(opportunityApplicantRepository, never()).existsAcceptedApplicationBetween(any(), any());
    }
}
