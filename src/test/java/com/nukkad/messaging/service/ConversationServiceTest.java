package com.nukkad.messaging.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.feed.service.FeedService;
import com.nukkad.investor.repository.IntroRequestRepository;
import com.nukkad.messaging.dto.ConversationAttachmentRef;
import com.nukkad.messaging.dto.MessageDto;
import com.nukkad.messaging.entity.Conversation;
import com.nukkad.messaging.entity.ConversationParticipant;
import com.nukkad.messaging.entity.Message;
import com.nukkad.messaging.entity.MessageDeletion;
import com.nukkad.messaging.repository.ConversationParticipantRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock private ConversationParticipantRepository participantRepository;
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
    @Mock private FileStorageService fileStorageService;

    /** A chat-attachment key exactly as {@code uploadAttachment} mints it for conv1, and the older flat layout. */
    private static final String KEY = "messages/conv1/3f2b8c1e-1111-4222-8333-444455556666.png";
    private static final String LEGACY_KEY = "messages/3f2b8c1e-1111-4222-8333-444455556666.png";

    private ConversationService service() {
        return new ConversationService(conversationRepository, participantRepository, messageRepository, messageDeletionRepository,
                encryptionService, messagingTemplate, userBlockRepository, connectionRepository,
                opportunityApplicantRepository, startupTeamMemberRepository, introRequestRepository,
                privacySettingsService, feedService, fileStorageService);
    }

    private Conversation conversation(String senderId, String recipientId) {
        return Conversation.builder().id("conv1").userAId(senderId).userBId(recipientId).build();
    }

    private Conversation groupConversation() {
        return Conversation.builder().id("conv1").conversationType(Conversation.Type.GROUP).groupName("Test Group").build();
    }

    private ConversationParticipant participant(String userId, ConversationParticipant.Role role) {
        return ConversationParticipant.builder().id(userId + "-p").conversationId("conv1").userId(userId).role(role)
                .joinedAt(Instant.now()).build();
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

    // ---- A new message reaches online recipients through the per-user topic (source of the in-app toast) ----
    // It deliberately does NOT create a persistent notification: chat would bury the notification center.

    @Test
    void sendingAMessagePushesTheConversationUpdateToTheRecipientsPerUserTopic() {
        Conversation conv = conversation("sender1", "recipient1");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("sender1", "recipient1")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("sender1", "recipient1")).thenReturn(true);
        when(privacySettingsService.canMessage("recipient1", true)).thenReturn(true);
        stubMessagePersistenceAndEncryption();
        stubConversationDtoLookups("recipient1", "sender1");

        service().sendMessage("conv1", "sender1", "Hello!", null, null);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/users/recipient1/conversations"), payload.capture());
        // The toast needs the conversation id to navigate to; it must come from this event itself.
        assertThat(payload.getValue()).isInstanceOfSatisfying(com.nukkad.messaging.dto.ConversationDto.class,
                dto -> assertThat(dto.id()).isEqualTo("conv1"));
        // The sender is never pushed their own message as an incoming one.
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/users/sender1/conversations"), any(Object.class));
    }

    @Test
    void sendingAGroupMessagePushesToEveryOtherParticipantButNotTheSender() {
        Conversation conv = groupConversation();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(participantRepository.existsByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "sender1")).thenReturn(true);
        when(participantRepository.findByConversationIdAndDeletedAtIsNull("conv1")).thenReturn(List.of(
                participant("sender1", ConversationParticipant.Role.MEMBER),
                participant("member2", ConversationParticipant.Role.MEMBER),
                participant("member3", ConversationParticipant.Role.MEMBER)));
        when(participantRepository.findByConversationIdAndUserIdAndDeletedAtIsNull(eq("conv1"), anyString()))
                .thenAnswer(inv -> Optional.of(participant(inv.getArgument(1), ConversationParticipant.Role.MEMBER)));
        stubMessagePersistenceAndEncryption();
        when(messageRepository.findLatestVisibleForViewer(eq("conv1"), anyString(), any())).thenReturn(List.of());
        when(messageRepository.countUnreadSinceForViewer(eq("conv1"), anyString(), any())).thenReturn(0L);

        service().sendMessage("conv1", "sender1", "Hey team", null, null);

        verify(messagingTemplate).convertAndSend(eq("/topic/users/member2/conversations"), any(Object.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/users/member3/conversations"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/users/sender1/conversations"), any(Object.class));
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

        MessageDto dto = service().sendMessage("conv1", "applicant1", "Hello!", null, null);

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

        assertThatThrownBy(() -> service().sendMessage("conv1", "stranger1", "Hi", null, null))
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

        MessageDto dto = service().sendMessage("conv1", "teammate1", "Hello!", null, null);

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

        assertThatThrownBy(() -> service().sendMessage("conv1", "requester1", "Hi", null, null))
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

        MessageDto dto = service().sendMessage("conv1", "investor1", "Hello!", null, null);

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

        assertThatThrownBy(() -> service().sendMessage("conv1", "investor1", "Hi", null, null))
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

        MessageDto dto = service().sendMessage("conv1", "friend1", "Hello!", null, null);

        assertThat(dto.content()).isEqualTo("Hello!");
        // Java's || short-circuits once the real connection check is true — the opportunity-based
        // exception is never even consulted for an already-connected pair.
        verify(opportunityApplicantRepository, never()).existsAcceptedApplicationBetween(any(), any());
    }

    // ---- Broadcast deferred until commit, so a recipient's mark-as-read can't race the insert ----

    @Test
    void sendMessageBroadcastIsDeferredUntilTransactionCommits() {
        Conversation conv = conversation("friend1", "owner1");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("friend1", "owner1")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("friend1", "owner1")).thenReturn(true);
        when(privacySettingsService.canMessage("owner1", true)).thenReturn(true);
        stubMessagePersistenceAndEncryption();
        stubConversationDtoLookups("owner1", "friend1");

        TransactionSynchronizationManager.initSynchronization();
        try {
            service().sendMessage("conv1", "friend1", "Hello!", null, null);

            // Still "inside" the transaction (never committed) — nothing should be broadcast yet.
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv1"), any(MessageDto.class));
            verify(messagingTemplate).convertAndSend(eq("/topic/users/owner1/conversations"), any(Object.class));
        } finally {
            // Thread-bound static state — must not leak into the other tests in this class.
            TransactionSynchronizationManager.clearSynchronization();
        }
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
                .isInstanceOf(ResourceNotFoundException.class);

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

    // ---- GROUP conversations: sendMessage fan-out, DIRECT path unchanged, non-member rejected ----

    @Test
    void sendingToAGroupBroadcastsOnceToTheConversationTopicAndOncePerNonSenderParticipant() {
        Conversation group = groupConversation();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(group));
        when(participantRepository.existsByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "alice")).thenReturn(true);
        when(participantRepository.findByConversationIdAndDeletedAtIsNull("conv1")).thenReturn(List.of(
                participant("alice", ConversationParticipant.Role.ADMIN),
                participant("bob", ConversationParticipant.Role.MEMBER),
                participant("carol", ConversationParticipant.Role.MEMBER)));
        when(participantRepository.findByConversationIdAndUserIdAndDeletedAtIsNull(eq("conv1"), anyString()))
                .thenAnswer(inv -> Optional.of(participant(inv.getArgument(1), ConversationParticipant.Role.MEMBER)));
        stubMessagePersistenceAndEncryption();
        when(messageRepository.findLatestVisibleForViewer(eq("conv1"), anyString(), any())).thenReturn(List.of());
        when(messageRepository.countUnreadSinceForViewer(eq("conv1"), anyString(), any())).thenReturn(0L);

        MessageDto dto = service().sendMessage("conv1", "alice", "Hello group!", null, null);

        assertThat(dto.content()).isEqualTo("Hello!"); // stubMessagePersistenceAndEncryption() decrypts to this
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv1"), any(MessageDto.class));
        // One sidebar-refresh push per non-sender participant (bob, carol) — never one for alice herself.
        verify(messagingTemplate).convertAndSend(eq("/topic/users/bob/conversations"), any(Object.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/users/carol/conversations"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/users/alice/conversations"), any(Object.class));
    }

    @Test
    void sendingToAGroupNeverConsultsDirectOnlyBlockOrPrivacyChecks() {
        Conversation group = groupConversation();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(group));
        when(participantRepository.existsByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "alice")).thenReturn(true);
        when(participantRepository.findByConversationIdAndDeletedAtIsNull("conv1")).thenReturn(List.of(
                participant("alice", ConversationParticipant.Role.ADMIN),
                participant("bob", ConversationParticipant.Role.MEMBER)));
        when(participantRepository.findByConversationIdAndUserIdAndDeletedAtIsNull(eq("conv1"), anyString()))
                .thenAnswer(inv -> Optional.of(participant(inv.getArgument(1), ConversationParticipant.Role.MEMBER)));
        stubMessagePersistenceAndEncryption();
        when(messageRepository.findLatestVisibleForViewer(eq("conv1"), anyString(), any())).thenReturn(List.of());
        when(messageRepository.countUnreadSinceForViewer(eq("conv1"), anyString(), any())).thenReturn(0L);

        service().sendMessage("conv1", "alice", "Hi group", null, null);

        verify(userBlockRepository, never()).existsBetween(any(), any());
        verify(privacySettingsService, never()).canMessage(any(), anyBoolean());
    }

    @Test
    void directSendMessageStillUsesTheOriginalSingleRecipientPathUnchanged() {
        Conversation conv = conversation("alice", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("alice", "bob")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("alice", "bob")).thenReturn(true);
        when(privacySettingsService.canMessage("bob", true)).thenReturn(true);
        stubMessagePersistenceAndEncryption();
        stubConversationDtoLookups("bob", "alice");

        service().sendMessage("conv1", "alice", "Hi", null, null);

        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv1"), any(MessageDto.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/users/bob/conversations"), any(Object.class));
        verify(participantRepository, never()).findByConversationIdAndDeletedAtIsNull(any());
    }

    @Test
    void nonMemberCannotSendToAGroupTheyAreNotPartOf() {
        Conversation group = groupConversation();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(group));
        when(participantRepository.existsByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "mallory")).thenReturn(false);

        assertThatThrownBy(() -> service().sendMessage("conv1", "mallory", "Hi", null, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void replyingToAMessageFromAnotherConversationIsRejected() {
        Conversation conv = conversation("alice", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("alice", "bob")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("alice", "bob")).thenReturn(true);
        when(privacySettingsService.canMessage("bob", true)).thenReturn(true);
        when(messageRepository.findById("other-conv-msg"))
                .thenReturn(Optional.of(message("other-conv-msg", "some-other-conv", "bob")));

        assertThatThrownBy(() -> service().sendMessage("conv1", "alice", "Hi", null, "other-conv-msg"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(messageRepository, never()).saveAndFlush(any());
    }

    // ---- markRead records a real readAt timestamp (backs the frontend's "Seen <time>" status) ----

    @Test
    void markingADirectConversationReadRecordsARealReadTimestamp() {
        Conversation conv = conversation("bob", "alice");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.markConversationRead(eq("conv1"), eq("bob"), any(Instant.class))).thenReturn(2);

        service().markRead("conv1", "bob");

        ArgumentCaptor<Instant> readAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(messageRepository).markConversationRead(eq("conv1"), eq("bob"), readAtCaptor.capture());
        assertThat(readAtCaptor.getValue()).isCloseTo(Instant.now(), within(2, java.time.temporal.ChronoUnit.SECONDS));
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv1/read"), any(Object.class));
    }

    @Test
    void markingAConversationReadDoesNotBroadcastWhenNothingWasActuallyUnread() {
        Conversation conv = conversation("bob", "alice");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.markConversationRead(eq("conv1"), eq("bob"), any(Instant.class))).thenReturn(0);

        service().markRead("conv1", "bob");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    // ---- Edit: sender-only, TEXT-only, unchanged content is a no-op, broadcasts on the conversation topic ----

    @Test
    void senderCanEditTheirOwnTextMessageAndItBroadcastsTheUpdate() {
        Conversation conv = conversation("alice", "bob");
        Message msg = message("msg1", "conv1", "alice");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(msg));
        when(encryptionService.decrypt("ciphertext")).thenReturn("Hi");
        when(encryptionService.encrypt("Hello")).thenReturn("new-ciphertext");
        when(encryptionService.decrypt("new-ciphertext")).thenReturn("Hello");

        MessageDto dto = service().editMessage("conv1", "alice", "msg1", "Hello");

        assertThat(dto.content()).isEqualTo("Hello");
        assertThat(dto.editedAt()).isNotNull();
        assertThat(msg.getContentCiphertext()).isEqualTo("new-ciphertext");
        verify(messageRepository).save(msg);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv1"), any(MessageDto.class));
    }

    @Test
    void nonSenderCannotEditAnotherUsersMessage() {
        Conversation conv = conversation("alice", "bob");
        Message msg = message("msg1", "conv1", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> service().editMessage("conv1", "alice", "msg1", "Hacked"))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void editingWithBlankContentIsRejectedAndOriginalStaysIntact() {
        Conversation conv = conversation("alice", "bob");
        Message msg = message("msg1", "conv1", "alice");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> service().editMessage("conv1", "alice", "msg1", "   "))
                .isInstanceOf(BadRequestException.class);

        assertThat(msg.getContentCiphertext()).isEqualTo("ciphertext");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void editingWithUnchangedContentSkipsPersistingAndBroadcasting() {
        Conversation conv = conversation("alice", "bob");
        Message msg = message("msg1", "conv1", "alice");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(msg));
        when(encryptionService.decrypt("ciphertext")).thenReturn("Hi");

        MessageDto dto = service().editMessage("conv1", "alice", "msg1", "Hi");

        assertThat(dto.content()).isEqualTo("Hi");
        assertThat(dto.editedAt()).isNull();
        verify(messageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void editingASharedPostMessageIsRejected() {
        Conversation conv = conversation("alice", "bob");
        Message msg = Message.builder().id("msg1").conversationId("conv1").senderId("alice")
                .messageType(Message.Type.SHARED_POST).sharedPostId("post1").contentCiphertext("ciphertext").build();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> service().editMessage("conv1", "alice", "msg1", "New caption"))
                .isInstanceOf(BadRequestException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    void editingAnAlreadyUnsentMessageIsRejected() {
        Conversation conv = conversation("alice", "bob");
        Message msg = message("msg1", "conv1", "alice");
        msg.setUnsentAt(Instant.now());
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> service().editMessage("conv1", "alice", "msg1", "New text"))
                .isInstanceOf(BadRequestException.class);

        verify(messageRepository, never()).save(any());
    }

    // ---- Unsend: sender-only, global (not per-viewer), wipes ciphertext, broadcasts ----

    @Test
    void senderCanUnsendTheirOwnMessageAndContentIsWipedForEveryone() {
        Conversation conv = conversation("alice", "bob");
        Message msg = message("msg1", "conv1", "alice");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(msg));
        when(encryptionService.encrypt("")).thenReturn("empty-ciphertext");

        MessageDto dto = service().unsendMessage("conv1", "alice", "msg1");

        assertThat(dto.content()).isEmpty();
        assertThat(dto.unsentAt()).isNotNull();
        assertThat(dto.sharedPost()).isNull();
        assertThat(msg.getContentCiphertext()).isEqualTo("empty-ciphertext");
        assertThat(msg.getUnsentAt()).isNotNull();
        // The row itself is kept (never hard-deleted) so reply references, ordering and pagination
        // stay intact — only its content is wiped and the tombstone timestamp is set.
        verify(messageRepository, never()).deleteById(any());
        verify(messageRepository, never()).delete(any());
        verify(messageRepository).save(msg);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv1"), any(MessageDto.class));
    }

    @Test
    void nonSenderCannotUnsendAnotherUsersMessage() {
        Conversation conv = conversation("alice", "bob");
        Message msg = message("msg1", "conv1", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> service().unsendMessage("conv1", "alice", "msg1"))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void unsendingAnAlreadyUnsentMessageIsAHarmlessNoOpButStillReturnsTheTombstone() {
        Conversation conv = conversation("alice", "bob");
        Message msg = message("msg1", "conv1", "alice");
        Instant firstUnsentAt = Instant.now().minusSeconds(60);
        msg.setUnsentAt(firstUnsentAt);
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(msg));

        MessageDto dto = service().unsendMessage("conv1", "alice", "msg1");

        assertThat(dto.unsentAt()).isEqualTo(firstUnsentAt);
        verify(messageRepository, never()).save(any());
        verify(encryptionService, never()).encrypt(any());
    }

    // ---- Replies to an unsent original degrade gracefully instead of leaking its old text ----

    @Test
    void replyPreviewShowsUnsentPlaceholderWhenTheOriginalWasUnsent() {
        Conversation conv = conversation("alice", "bob");
        Message original = message("msg1", "conv1", "bob");
        original.setUnsentAt(Instant.now());
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("alice", "bob")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("alice", "bob")).thenReturn(true);
        when(privacySettingsService.canMessage("bob", true)).thenReturn(true);
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(original));
        stubMessagePersistenceAndEncryption();
        stubConversationDtoLookups("bob", "alice");

        MessageDto dto = service().sendMessage("conv1", "alice", "Hello!", null, "msg1");

        assertThat(dto.replyTo()).isNotNull();
        assertThat(dto.replyTo().contentSnippet()).isEqualTo("This message was unsent");
    }

    // ---- Attachments: upload is participant-gated, sendMessage stores/derives type, unsend deletes from storage ----

    @Test
    void uploadingAnAttachmentRequiresBeingAParticipant() {
        Conversation conv = conversation("alice", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        MultipartFile file = mock(MultipartFile.class);

        assertThatThrownBy(() -> service().uploadAttachment("conv1", "mallory", file))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(fileStorageService, never()).storeConversationAttachment(any(), anyString());
    }

    @Test
    void uploadingAnAttachmentStoresItPrivatelyUnderThisConversationsPrefixAndReturnsAKeyNotAUrl() {
        Conversation conv = conversation("alice", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("photo.png");
        when(fileStorageService.storeConversationAttachment(file, "messages/conv1"))
                .thenReturn(new FileStorageService.StoredPrivateMedia(KEY, FileStorageService.AttachmentKind.IMAGE));

        ConversationAttachmentRef ref = service().uploadAttachment("conv1", "alice", file);

        assertThat(ref.key()).isEqualTo(KEY);
        assertThat(ref.kind()).isEqualTo("IMAGE");
        assertThat(ref.fileName()).isEqualTo("photo.png");
    }

    @Test
    void theFileNameHandedBackAfterAnUploadIsSanitizedNotTheRawClientString() {
        Conversation conv = conversation("alice", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("C:\\fakepath\\..\\..\\holiday\u202Egpj.exe");
        when(fileStorageService.storeConversationAttachment(file, "messages/conv1"))
                .thenReturn(new FileStorageService.StoredPrivateMedia(KEY, FileStorageService.AttachmentKind.IMAGE));

        ConversationAttachmentRef ref = service().uploadAttachment("conv1", "alice", file);

        assertThat(ref.fileName()).isEqualTo("holidaygpj.exe");
    }

    @Test
    void sendingAMessageWithOnlyAnAttachmentAndNoCaptionDerivesTheMessageTypeFromIt() {
        Conversation conv = conversation("alice", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("alice", "bob")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("alice", "bob")).thenReturn(true);
        when(privacySettingsService.canMessage("bob", true)).thenReturn(true);
        stubMessagePersistenceAndEncryption();
        stubConversationDtoLookups("bob", "alice");
        when(fileStorageService.describePrivateObject(KEY)).thenReturn(Optional.of(FileStorageService.AttachmentKind.IMAGE));
        when(fileStorageService.presignGet(eq(KEY), any(java.time.Duration.class)))
                .thenReturn("https://cdn.example.com/" + KEY + "?X-Amz-Signature=abc");
        ConversationAttachmentRef attachment = new ConversationAttachmentRef(KEY, "IMAGE", "photo.png");

        MessageDto dto = service().sendMessage("conv1", "alice", "", null, null, attachment);

        assertThat(dto.type()).isEqualTo("IMAGE");
        assertThat(dto.attachment()).isNotNull();
        // The DTO carries a presigned URL, never the raw key — that never leaves the backend.
        assertThat(dto.attachment().url()).isEqualTo("https://cdn.example.com/" + KEY + "?X-Amz-Signature=abc");
        assertThat(dto.attachment().fileName()).isEqualTo("photo.png");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getMessageType()).isEqualTo(Message.Type.IMAGE);
        assertThat(captor.getValue().getAttachmentKey()).isEqualTo(KEY);
    }

    @Test
    void aRestMessageListFetchAndAWebSocketBroadcastBothGetFreshlyPresignedUrlsFromTheSameCode() {
        // toMessageDto is the one place both the REST list path (getMessages) and the WS broadcast in
        // sendMessage build a MessageDto — this pins that down so a future refactor can't split them.
        Conversation conv = conversation("alice", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findVisibleForViewer(eq("conv1"), eq("alice"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(
                        Message.builder().id("msg1").conversationId("conv1").senderId("bob")
                                .messageType(Message.Type.IMAGE).attachmentKey(KEY).attachmentKind("IMAGE")
                                .contentCiphertext("ciphertext").build())));
        when(encryptionService.decrypt("ciphertext")).thenReturn("");
        when(fileStorageService.presignGet(eq(KEY), any(java.time.Duration.class)))
                .thenReturn("https://cdn.example.com/" + KEY + "?X-Amz-Signature=fresh");

        var page = service().getMessages("conv1", "alice", 0, 20);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).attachment().url()).isEqualTo("https://cdn.example.com/" + KEY + "?X-Amz-Signature=fresh");
        // Short-lived by design: an expired URL is re-minted on demand (getAttachment), not held open for hours.
        verify(fileStorageService).presignGet(KEY, java.time.Duration.ofHours(1));
    }

    @Test
    void sendingAMessageWithNoContentNoAttachmentAndNoSharedPostIsStillRejected() {
        Conversation conv = conversation("alice", "bob");
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("alice", "bob")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("alice", "bob")).thenReturn(true);
        when(privacySettingsService.canMessage("bob", true)).thenReturn(true);

        assertThatThrownBy(() -> service().sendMessage("conv1", "alice", "   ", null, null, null))
                .isInstanceOf(BadRequestException.class);

        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void unsendingAMessageWithAnAttachmentDeletesItFromStorageAndClearsTheAttachmentFields() {
        Conversation conv = conversation("alice", "bob");
        Message msg = Message.builder().id("msg1").conversationId("conv1").senderId("alice")
                .messageType(Message.Type.IMAGE).attachmentKey(KEY)
                .attachmentKind("IMAGE").attachmentFileName("photo.png").contentCiphertext("ciphertext").build();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(msg));
        when(encryptionService.encrypt("")).thenReturn("empty-ciphertext");

        MessageDto dto = service().unsendMessage("conv1", "alice", "msg1");

        assertThat(dto.attachment()).isNull();
        assertThat(msg.getAttachmentKey()).isNull();
        assertThat(msg.getAttachmentKind()).isNull();
        assertThat(msg.getAttachmentFileName()).isNull();
        verify(fileStorageService).deleteByKey(KEY);
        verify(fileStorageService, never()).deleteIfHosted(any());
    }

    @Test
    void replyPreviewToACaptionlessPhotoShowsAPhotoLabelInsteadOfABlankSnippet() {
        Conversation conv = conversation("alice", "bob");
        Message original = Message.builder().id("msg1").conversationId("conv1").senderId("bob")
                .messageType(Message.Type.IMAGE).attachmentKey("messages/x.png")
                .attachmentKind("IMAGE").contentCiphertext("empty-ciphertext").build();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(userBlockRepository.existsBetween("alice", "bob")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("alice", "bob")).thenReturn(true);
        when(privacySettingsService.canMessage("bob", true)).thenReturn(true);
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(original));
        stubMessagePersistenceAndEncryption();
        when(encryptionService.decrypt("empty-ciphertext")).thenReturn("");
        stubConversationDtoLookups("bob", "alice");

        MessageDto dto = service().sendMessage("conv1", "alice", "Nice!", null, "msg1");

        assertThat(dto.replyTo()).isNotNull();
        assertThat(dto.replyTo().contentSnippet()).isEqualTo("Photo");
    }

    // =====================================================================================================
    // Security regression tests: conversation / message / attachment authorization
    // =====================================================================================================

    /** Every operation that takes a conversation id, called as {@code userId} — the full surface a hostile
     * client can reach by swapping the id in the URL. */
    private Map<String, ThrowingCallable> conversationScopedOperations(String conversationId, String userId) {
        MultipartFile file = mock(MultipartFile.class);
        Map<String, ThrowingCallable> ops = new LinkedHashMap<>();
        ops.put("getMessages", () -> service().getMessages(conversationId, userId, 0, 20));
        ops.put("sendMessage", () -> service().sendMessage(conversationId, userId, "hi", null, null));
        ops.put("uploadAttachment", () -> service().uploadAttachment(conversationId, userId, file));
        ops.put("markRead", () -> service().markRead(conversationId, userId));
        ops.put("toggleMute", () -> service().toggleMute(conversationId, userId));
        ops.put("setNickname", () -> service().setNickname(conversationId, userId, "nick"));
        ops.put("deleteConversation", () -> service().deleteConversation(conversationId, userId));
        ops.put("hideMessagesForViewer", () -> service().hideMessagesForViewer(conversationId, userId, List.of("msg1")));
        ops.put("editMessage", () -> service().editMessage(conversationId, userId, "msg1", "edited"));
        ops.put("unsendMessage", () -> service().unsendMessage(conversationId, userId, "msg1"));
        ops.put("getAttachment", () -> service().getAttachment(conversationId, userId, "msg1"));
        return ops;
    }

    @Test
    void everyConversationScopedOperationTreatsANonParticipantExactlyLikeAMissingConversation() {
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation("alice", "bob")));
        when(conversationRepository.findById("missing")).thenReturn(Optional.empty());

        // Same exception type AND same message for "exists but not yours" and "doesn't exist": a 403 for one and
        // a 404 for the other would let anyone enumerate real conversation ids by trying them.
        conversationScopedOperations("conv1", "mallory").forEach((name, operation) ->
                assertThatThrownBy(operation).as(name).isInstanceOf(ResourceNotFoundException.class).hasMessage("Conversation not found"));
        conversationScopedOperations("missing", "mallory").forEach((name, operation) ->
                assertThatThrownBy(operation).as(name).isInstanceOf(ResourceNotFoundException.class).hasMessage("Conversation not found"));

        // Nothing past the participant check was ever reached — no data read, no write, no storage, no broadcast.
        verifyNoInteractions(messageRepository, messageDeletionRepository, fileStorageService, messagingTemplate, encryptionService);
    }

    @Test
    void aFormerGroupMemberIsTreatedLikeAStrangerForEveryOperation() {
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(groupConversation()));
        when(participantRepository.existsByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "left-the-group")).thenReturn(false);

        conversationScopedOperations("conv1", "left-the-group").forEach((name, operation) ->
                assertThatThrownBy(operation).as(name).isInstanceOf(ResourceNotFoundException.class).hasMessage("Conversation not found"));

        verifyNoInteractions(messageRepository, messageDeletionRepository, fileStorageService, messagingTemplate);
    }

    @Test
    void aParticipantOfAnotherConversationCannotEditUnsendHideOrFetchAMessageByGuessingItsId() {
        // Mallory legitimately belongs to conv2 — and tries to act on alice's message in conv1 through it.
        when(conversationRepository.findById("conv2")).thenReturn(Optional.of(
                Conversation.builder().id("conv2").userAId("carol").userBId("mallory").build()));
        Message alicesMessage = Message.builder().id("msg1").conversationId("conv1").senderId("alice")
                .messageType(Message.Type.IMAGE).attachmentKey(KEY).attachmentKind("IMAGE").contentCiphertext("ciphertext").build();
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(alicesMessage));
        when(messageRepository.findAllById(any())).thenReturn(List.of(alicesMessage));

        assertThatThrownBy(() -> service().editMessage("conv2", "mallory", "msg1", "pwned")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().unsendMessage("conv2", "mallory", "msg1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().hideMessagesForViewer("conv2", "mallory", List.of("msg1"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().getAttachment("conv2", "mallory", "msg1")).isInstanceOf(ResourceNotFoundException.class);

        assertThat(alicesMessage.getUnsentAt()).isNull();
        assertThat(alicesMessage.getAttachmentKey()).isEqualTo(KEY);
        verify(messageRepository, never()).save(any());
        verify(messageDeletionRepository, never()).saveAll(any());
        verify(fileStorageService, never()).deleteByKey(any());
        verify(fileStorageService, never()).presignGet(any(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void aParticipantCannotUnsendOrEditTheOtherParticipantsMessageAndItsFileIsLeftAlone() {
        Message bobsMessage = Message.builder().id("msg1").conversationId("conv1").senderId("bob")
                .messageType(Message.Type.IMAGE).attachmentKey(KEY).attachmentKind("IMAGE").contentCiphertext("ciphertext").build();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation("alice", "bob")));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(bobsMessage));

        assertThatThrownBy(() -> service().unsendMessage("conv1", "alice", "msg1")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service().editMessage("conv1", "alice", "msg1", "x")).isInstanceOf(ForbiddenException.class);

        assertThat(bobsMessage.getUnsentAt()).isNull();
        verify(fileStorageService, never()).deleteByKey(any());
        verify(messageRepository, never()).save(any());
    }

    // ---- Presigned URLs are only ever minted for an authorized viewer, from ids, never from a client key ----

    @Test
    void anAuthorizedParticipantGetsAFreshShortLivedUrlForAMessagesAttachment() {
        Message message = Message.builder().id("msg1").conversationId("conv1").senderId("bob")
                .messageType(Message.Type.IMAGE).attachmentKey(KEY).attachmentKind("IMAGE").attachmentFileName("photo.png")
                .contentCiphertext("ciphertext").build();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation("alice", "bob")));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));
        when(messageDeletionRepository.findDeletedMessageIds("alice", List.of("msg1"))).thenReturn(Set.of());
        when(fileStorageService.presignGet(KEY, java.time.Duration.ofHours(1))).thenReturn("https://media.example.com/" + KEY + "?X-Amz-Expires=3600");

        var attachment = service().getAttachment("conv1", "alice", "msg1");

        assertThat(attachment.url()).isEqualTo("https://media.example.com/" + KEY + "?X-Amz-Expires=3600");
        assertThat(attachment.fileName()).isEqualTo("photo.png");
    }

    @Test
    void noUrlIsMintedForAMessageTheViewerHasHiddenThatWasUnsentOrThatHasNoAttachment() {
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation("alice", "bob")));

        Message hidden = Message.builder().id("hidden").conversationId("conv1").senderId("bob")
                .messageType(Message.Type.IMAGE).attachmentKey(KEY).attachmentKind("IMAGE").contentCiphertext("c").build();
        when(messageRepository.findById("hidden")).thenReturn(Optional.of(hidden));
        when(messageDeletionRepository.findDeletedMessageIds("alice", List.of("hidden"))).thenReturn(Set.of("hidden"));

        Message unsent = Message.builder().id("unsent").conversationId("conv1").senderId("bob")
                .messageType(Message.Type.IMAGE).attachmentKey(KEY).attachmentKind("IMAGE").contentCiphertext("c").build();
        unsent.setUnsentAt(Instant.now());
        when(messageRepository.findById("unsent")).thenReturn(Optional.of(unsent));

        Message textOnly = message("text", "conv1", "bob");
        when(messageRepository.findById("text")).thenReturn(Optional.of(textOnly));

        assertThatThrownBy(() -> service().getAttachment("conv1", "alice", "hidden")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().getAttachment("conv1", "alice", "unsent")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().getAttachment("conv1", "alice", "text")).isInstanceOf(ResourceNotFoundException.class);
        verify(fileStorageService, never()).presignGet(any(), any());
    }

    @Test
    void aMessageRowWhoseKeyIsOutsideTheChatAttachmentLayoutNeverGetsASignedUrl() {
        // Rows written before keys were validated could point anywhere in the bucket. Reading one back must not
        // sign whatever the key names — it gets no URL, and the on-demand endpoint says "not found".
        Message poisoned = Message.builder().id("msg1").conversationId("conv1").senderId("bob")
                .messageType(Message.Type.IMAGE).attachmentKey("startup-materials/3f2b8c1e-1111-4222-8333-444455556666.pdf")
                .attachmentKind("PDF").attachmentFileName("deck.pdf").contentCiphertext("ciphertext").build();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation("alice", "bob")));
        when(messageRepository.findVisibleForViewer(eq("conv1"), eq("alice"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(poisoned)));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(poisoned));
        when(messageDeletionRepository.findDeletedMessageIds("alice", List.of("msg1"))).thenReturn(Set.of());
        when(encryptionService.decrypt("ciphertext")).thenReturn("");

        var page = service().getMessages("conv1", "alice", 0, 20);
        assertThat(page.getContent().get(0).attachment().url()).isNull();
        assertThatThrownBy(() -> service().getAttachment("conv1", "alice", "msg1")).isInstanceOf(ResourceNotFoundException.class);

        verify(fileStorageService, never()).presignGet(any(), any());
    }

    @Test
    void aChatAttachmentFromTheOlderFlatLayoutStillOpens() {
        Message legacy = Message.builder().id("msg1").conversationId("conv1").senderId("bob")
                .messageType(Message.Type.IMAGE).attachmentKey(LEGACY_KEY).attachmentKind("IMAGE").contentCiphertext("c").build();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation("alice", "bob")));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(legacy));
        when(messageDeletionRepository.findDeletedMessageIds("alice", List.of("msg1"))).thenReturn(Set.of());
        when(fileStorageService.presignGet(eq(LEGACY_KEY), any(java.time.Duration.class))).thenReturn("https://media.example.com/legacy");

        assertThat(service().getAttachment("conv1", "alice", "msg1").url()).isEqualTo("https://media.example.com/legacy");
    }

    // ---- The attachment key a client sends back is never trusted (arbitrary-object presign / delete) ----

    private void stubAliceMayMessageBob() {
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation("alice", "bob")));
        when(userBlockRepository.existsBetween("alice", "bob")).thenReturn(false);
        when(connectionRepository.existsAcceptedBetween("alice", "bob")).thenReturn(true);
        when(privacySettingsService.canMessage("bob", true)).thenReturn(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // another conversation's file
            "messages/conv2/3f2b8c1e-1111-4222-8333-444455556666.png",
            // every other prefix in the bucket: private feed/startup files, public avatars and resources
            "feed/3f2b8c1e-1111-4222-8333-444455556666.png",
            "startup-materials/3f2b8c1e-1111-4222-8333-444455556666.pdf",
            "avatars/3f2b8c1e-1111-4222-8333-444455556666.png",
            "resources/3f2b8c1e-1111-4222-8333-444455556666.pdf",
            // the old flat layout can't be tied to this conversation, so it can never be ATTACHED (only read)
            "messages/3f2b8c1e-1111-4222-8333-444455556666.png",
            // traversal / prefix-escape / absolute path / full URL
            "messages/conv1/../../feed/3f2b8c1e-1111-4222-8333-444455556666.png",
            "messages/../feed/x.png",
            "/messages/conv1/3f2b8c1e-1111-4222-8333-444455556666.png",
            "messages/conv1/3f2b8c1e-1111-4222-8333-444455556666.png/../../x",
            "messages//conv1/3f2b8c1e-1111-4222-8333-444455556666.png",
            "https://evil.example.com/x.png",
            // not a key this service mints
            "messages/conv1/3F2B8C1E-1111-4222-8333-444455556666.png",
            "messages/conv1/3f2b8c1e-1111-4222-8333-444455556666",
            "messages/conv1/photo.png",
    })
    void aKeyThatIsNotAFileThisServiceStoredForThisConversationCanNeverBeAttached(String hostileKey) {
        stubAliceMayMessageBob();
        ConversationAttachmentRef attachment = new ConversationAttachmentRef(hostileKey, "IMAGE", "photo.png");

        assertThatThrownBy(() -> service().sendMessage("conv1", "alice", "look", null, null, attachment))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This attachment can't be sent. Please upload it again.");

        // Refused on the key's SHAPE alone — storage is never even asked about it, nothing is saved, nothing signed.
        verify(fileStorageService, never()).describePrivateObject(any());
        verify(fileStorageService, never()).presignGet(any(), any());
        verify(messageRepository, never()).saveAndFlush(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void aFileAlreadyAttachedToAnotherMessageCannotBeAttachedAgain() {
        stubAliceMayMessageBob();
        when(messageRepository.existsByAttachmentKey(KEY)).thenReturn(true);

        assertThatThrownBy(() -> service().sendMessage("conv1", "alice", "", null, null, new ConversationAttachmentRef(KEY, "IMAGE", "p.png")))
                .isInstanceOf(BadRequestException.class);

        verify(fileStorageService, never()).describePrivateObject(any());
        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void aWellFormedKeyThatIsNotActuallyInStorageCannotBeAttached() {
        stubAliceMayMessageBob();
        when(fileStorageService.describePrivateObject(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().sendMessage("conv1", "alice", "", null, null, new ConversationAttachmentRef(KEY, "IMAGE", "p.png")))
                .isInstanceOf(BadRequestException.class);

        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void theMessageTypeComesFromWhatStorageHoldsNotFromWhatTheClientClaimed() {
        stubAliceMayMessageBob();
        stubMessagePersistenceAndEncryption();
        stubConversationDtoLookups("bob", "alice");
        when(fileStorageService.describePrivateObject(KEY)).thenReturn(Optional.of(FileStorageService.AttachmentKind.IMAGE));
        when(fileStorageService.presignGet(eq(KEY), any(java.time.Duration.class))).thenReturn("https://media.example.com/x");

        // The client says PDF; the stored object is an image.
        MessageDto dto = service().sendMessage("conv1", "alice", "", null, null, new ConversationAttachmentRef(KEY, "PDF", "report.pdf"));

        assertThat(dto.type()).isEqualTo("IMAGE");
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getMessageType()).isEqualTo(Message.Type.IMAGE);
        assertThat(captor.getValue().getAttachmentKind()).isEqualTo("IMAGE");
    }

    @Test
    void theStoredFileNameIsTheSanitizedOneNeverTheRawClientString() {
        stubAliceMayMessageBob();
        stubMessagePersistenceAndEncryption();
        stubConversationDtoLookups("bob", "alice");
        when(fileStorageService.describePrivateObject(KEY)).thenReturn(Optional.of(FileStorageService.AttachmentKind.IMAGE));
        when(fileStorageService.presignGet(eq(KEY), any(java.time.Duration.class))).thenReturn("https://media.example.com/x");

        service().sendMessage("conv1", "alice", "", null, null, new ConversationAttachmentRef(KEY, "IMAGE", "../../etc/passwd‮.png"));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAttachmentFileName()).isEqualTo("passwd.png");
    }

    // ---- Unsend only ever deletes a chat file that is this conversation's own, and unshared ----

    @Test
    void unsendingDeletesTheFileOnlyWhenNoOtherMessageStillUsesIt() {
        Message shared = Message.builder().id("msg1").conversationId("conv1").senderId("alice")
                .messageType(Message.Type.IMAGE).attachmentKey(KEY).attachmentKind("IMAGE").contentCiphertext("c").build();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation("alice", "bob")));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(shared));
        when(messageRepository.existsByAttachmentKeyAndIdNot(KEY, "msg1")).thenReturn(true);
        when(encryptionService.encrypt("")).thenReturn("empty");

        MessageDto dto = service().unsendMessage("conv1", "alice", "msg1");

        assertThat(dto.unsentAt()).isNotNull();          // the unsend itself still succeeds
        assertThat(shared.getAttachmentKey()).isNull();
        verify(fileStorageService, never()).deleteByKey(any()); // ...but the file another message still shows stays
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "avatars/3f2b8c1e-1111-4222-8333-444455556666.png",
            "resources/3f2b8c1e-1111-4222-8333-444455556666.pdf",
            "feed/3f2b8c1e-1111-4222-8333-444455556666.png",
            "startup-materials/3f2b8c1e-1111-4222-8333-444455556666.pdf",
            "messages/conv2/3f2b8c1e-1111-4222-8333-444455556666.png",
            "messages/../feed/x.png",
    })
    void unsendingNeverDeletesAnObjectThatIsNotThisConversationsChatFile(String foreignKey) {
        // A row that already points somewhere it shouldn't (written before key validation existed): unsending it
        // must not become a way to delete that other object from the bucket.
        Message poisoned = Message.builder().id("msg1").conversationId("conv1").senderId("alice")
                .messageType(Message.Type.IMAGE).attachmentKey(foreignKey).attachmentKind("IMAGE").contentCiphertext("c").build();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation("alice", "bob")));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(poisoned));
        when(encryptionService.encrypt("")).thenReturn("empty");

        MessageDto dto = service().unsendMessage("conv1", "alice", "msg1");

        assertThat(dto.unsentAt()).isNotNull();
        assertThat(poisoned.getAttachmentKey()).isNull();
        verify(fileStorageService, never()).deleteByKey(any());
        verify(fileStorageService, never()).deleteIfHosted(any());
    }

    @Test
    void unsendingAChatFileFromTheOlderFlatLayoutStillDeletesIt() {
        Message legacy = Message.builder().id("msg1").conversationId("conv1").senderId("alice")
                .messageType(Message.Type.IMAGE).attachmentKey(LEGACY_KEY).attachmentKind("IMAGE").contentCiphertext("c").build();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation("alice", "bob")));
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(legacy));
        when(encryptionService.encrypt("")).thenReturn("empty");

        service().unsendMessage("conv1", "alice", "msg1");

        verify(fileStorageService).deleteByKey(LEGACY_KEY);
    }

    // ---- "Delete for me" changes only the caller's own view ----

    @Test
    void deleteForMeRecordsADeletionForTheCallerOnlyAndNeverTouchesTheMessageOrTheStoredFile() {
        Message bobsMessage = Message.builder().id("msg1").conversationId("conv1").senderId("bob")
                .messageType(Message.Type.IMAGE).attachmentKey(KEY).attachmentKind("IMAGE").contentCiphertext("c").build();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conversation("alice", "bob")));
        when(messageRepository.findAllById(List.of("msg1"))).thenReturn(List.of(bobsMessage));
        when(messageDeletionRepository.findDeletedMessageIds("alice", List.of("msg1"))).thenReturn(Set.of());

        service().hideMessagesForViewer("conv1", "alice", List.of("msg1"));

        ArgumentCaptor<List<MessageDeletion>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageDeletionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getUserId()).isEqualTo("alice");
        assertThat(captor.getValue().get(0).getMessageId()).isEqualTo("msg1");
        // bob's (the sender's) view, the row itself and the stored file are all untouched.
        assertThat(bobsMessage.getUnsentAt()).isNull();
        assertThat(bobsMessage.getAttachmentKey()).isEqualTo(KEY);
        verify(messageRepository, never()).save(any());
        verify(fileStorageService, never()).deleteByKey(any());
    }
}
