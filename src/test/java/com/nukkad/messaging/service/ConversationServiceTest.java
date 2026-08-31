package com.nukkad.messaging.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.feed.service.FeedService;
import com.nukkad.investor.repository.IntroRequestRepository;
import com.nukkad.messaging.dto.MessageDto;
import com.nukkad.messaging.entity.Conversation;
import com.nukkad.messaging.entity.Message;
import com.nukkad.messaging.entity.MessageDeletion;
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
import org.mockito.ArgumentCaptor;
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

    private Message message(String id, String conversationId, String senderId) {
        return Message.builder().id(id).conversationId(conversationId).senderId(senderId)
                .contentCiphertext("ciphertext").build();
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

    // ---- "Delete for me": per-viewer message hiding (single + bulk) ----

    @Test
    void viewerCanHideAMessageTheyReceivedFromTheirOwnView() {
        // "Delete for me" is not restricted to messages you sent — Alice can hide Bob's message
        // from her own view too, since it only ever changes her own visibility.
        Conversation conv = conversation("alice", "bob");
        Message bobsMessage = message("msg1", "conv1", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findAllById(List.of("msg1"))).thenReturn(List.of(bobsMessage));
        when(messageDeletionRepository.findDeletedMessageIds("alice", List.of("msg1"))).thenReturn(java.util.Set.of());

        service().hideMessagesForViewer("conv1", "alice", List.of("msg1"));

        ArgumentCaptor<List<MessageDeletion>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageDeletionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getMessageId()).isEqualTo("msg1");
        assertThat(captor.getValue().get(0).getUserId()).isEqualTo("alice");
    }

    @Test
    void hidingAMessageNeverMutatesTheSharedMessageRowOrBroadcastsAnything() {
        // The shared Message entity itself must be completely untouched — hiding is purely a
        // MessageDeletion insert for the viewer — and no realtime event goes out, since only the
        // acting viewer's own view changes and their own client already has the result of this call.
        Conversation conv = conversation("alice", "bob");
        Message msg = message("msg1", "conv1", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findAllById(List.of("msg1"))).thenReturn(List.of(msg));
        when(messageDeletionRepository.findDeletedMessageIds("alice", List.of("msg1"))).thenReturn(java.util.Set.of());

        service().hideMessagesForViewer("conv1", "alice", List.of("msg1"));

        verify(messageRepository, never()).save(any());
        verify(messageRepository, never()).saveAll(any());
        verify(messagingTemplate, org.mockito.Mockito.never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void viewerCanBulkHideMultipleMessagesRegardlessOfSender() {
        Conversation conv = conversation("alice", "bob");
        Message m1 = message("msg1", "conv1", "alice");
        Message m2 = message("msg2", "conv1", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findAllById(List.of("msg1", "msg2"))).thenReturn(List.of(m1, m2));
        when(messageDeletionRepository.findDeletedMessageIds("alice", List.of("msg1", "msg2"))).thenReturn(java.util.Set.of());

        service().hideMessagesForViewer("conv1", "alice", List.of("msg1", "msg2"));

        ArgumentCaptor<List<MessageDeletion>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageDeletionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(MessageDeletion::getMessageId).containsExactlyInAnyOrder("msg1", "msg2");
        assertThat(captor.getValue()).allMatch(d -> d.getUserId().equals("alice"));
    }

    @Test
    void bulkHideWithOneMessageFromAnotherConversationHidesNothing() {
        Conversation conv = conversation("alice", "bob");
        Message ownConvMessage = message("msg1", "conv1", "alice");
        Message otherConvMessage = message("msg2", "some-other-conv", "alice");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findAllById(List.of("msg1", "msg2"))).thenReturn(List.of(ownConvMessage, otherConvMessage));

        assertThatThrownBy(() -> service().hideMessagesForViewer("conv1", "alice", List.of("msg1", "msg2")))
                .isInstanceOf(ResourceNotFoundException.class);

        // All-or-nothing: the one message that doesn't belong to this conversation blocks the batch.
        verify(messageDeletionRepository, never()).saveAll(any());
    }

    @Test
    void hidingANonexistentMessageIsNotFound() {
        Conversation conv = conversation("alice", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findAllById(List.of("ghost"))).thenReturn(List.of());

        assertThatThrownBy(() -> service().hideMessagesForViewer("conv1", "alice", List.of("ghost")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(messageDeletionRepository, never()).saveAll(any());
    }

    @Test
    void hidingAMessageFromAnotherConversationIsRejected() {
        Conversation conv = conversation("alice", "bob");
        Message otherConversationsMessage = message("msg1", "some-other-conv", "alice");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findAllById(List.of("msg1"))).thenReturn(List.of(otherConversationsMessage));

        assertThatThrownBy(() -> service().hideMessagesForViewer("conv1", "alice", List.of("msg1")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(messageDeletionRepository, never()).saveAll(any());
    }

    @Test
    void reHidingAnAlreadyHiddenMessageIsAHarmlessNoOp() {
        Conversation conv = conversation("alice", "bob");
        Message msg = message("msg1", "conv1", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findAllById(List.of("msg1"))).thenReturn(List.of(msg));
        // Alice already has a MessageDeletion row for msg1 — the dedup filter should drop it.
        when(messageDeletionRepository.findDeletedMessageIds("alice", List.of("msg1"))).thenReturn(java.util.Set.of("msg1"));

        service().hideMessagesForViewer("conv1", "alice", List.of("msg1"));

        ArgumentCaptor<List<MessageDeletion>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageDeletionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void hideMessagesRejectsAnEmptyIdList() {
        assertThatThrownBy(() -> service().hideMessagesForViewer("conv1", "alice", List.of()))
                .isInstanceOf(BadRequestException.class);

        verify(conversationRepository, never()).findById(any());
    }

    @Test
    void nonParticipantCannotHideMessagesInAConversationTheyAreNotPartOf() {
        Conversation conv = conversation("alice", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> service().hideMessagesForViewer("conv1", "mallory", List.of("msg1")))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).findAllById(any());
        verify(messageDeletionRepository, never()).saveAll(any());
    }

    @Test
    void deleteConversationStillWorksUnchangedAlongsideMessageDeletion() {
        Conversation conv = conversation("alice", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findByConversationId("conv1")).thenReturn(List.of(message("msg1", "conv1", "bob")));
        when(messageDeletionRepository.findDeletedMessageIds(eq("alice"), any())).thenReturn(java.util.Set.of());

        service().deleteConversation("conv1", "alice");

        assertThat(conv.getDeletedAtByUserA() != null || conv.getDeletedAtByUserB() != null).isTrue();
        verify(conversationRepository).save(conv);
        verify(messageDeletionRepository).saveAll(any());
    }
}
