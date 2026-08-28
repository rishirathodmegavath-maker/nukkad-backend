package com.nukkad.messaging.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.feed.dto.PostDto;
import com.nukkad.feed.service.FeedService;
import com.nukkad.investor.repository.IntroRequestRepository;
import com.nukkad.messaging.dto.ConversationDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageDeletionRepository messageDeletionRepository;
    private final MessageEncryptionService encryptionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserBlockRepository userBlockRepository;
    private final ConnectionRepository connectionRepository;
    private final OpportunityApplicantRepository opportunityApplicantRepository;
    private final StartupTeamMemberRepository startupTeamMemberRepository;
    private final IntroRequestRepository introRequestRepository;
    private final UserPrivacySettingsService privacySettingsService;
    private final FeedService feedService;

    public ConversationService(ConversationRepository conversationRepository,
                                MessageRepository messageRepository,
                                MessageDeletionRepository messageDeletionRepository,
                                MessageEncryptionService encryptionService,
                                SimpMessagingTemplate messagingTemplate,
                                UserBlockRepository userBlockRepository,
                                ConnectionRepository connectionRepository,
                                OpportunityApplicantRepository opportunityApplicantRepository,
                                StartupTeamMemberRepository startupTeamMemberRepository,
                                IntroRequestRepository introRequestRepository,
                                UserPrivacySettingsService privacySettingsService,
                                FeedService feedService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.messageDeletionRepository = messageDeletionRepository;
        this.encryptionService = encryptionService;
        this.messagingTemplate = messagingTemplate;
        this.userBlockRepository = userBlockRepository;
        this.connectionRepository = connectionRepository;
        this.opportunityApplicantRepository = opportunityApplicantRepository;
        this.startupTeamMemberRepository = startupTeamMemberRepository;
        this.introRequestRepository = introRequestRepository;
        this.privacySettingsService = privacySettingsService;
        this.feedService = feedService;
    }

    @Transactional
    public ConversationDto getOrCreate(String viewerId, String otherUserId) {
        if (viewerId.equals(otherUserId)) {
            throw new BadRequestException("Cannot start a conversation with yourself");
        }
        Conversation conversation = findOrCreatePair(viewerId, otherUserId);
        return toDto(conversation, viewerId);
    }

    private Conversation findOrCreatePair(String initiatorId, String recipientId) {
        String a = initiatorId.compareTo(recipientId) < 0 ? initiatorId : recipientId;
        String b = initiatorId.compareTo(recipientId) < 0 ? recipientId : initiatorId;
        return conversationRepository.findByUserAIdAndUserBId(a, b)
                .orElseGet(() -> {
                    // Only gate brand-new conversations — an existing conversation (e.g. from before
                    // a block or privacy change took effect) stays viewable for both sides.
                    requireCanInitiateConversation(initiatorId, recipientId);
                    return conversationRepository.saveAndFlush(Conversation.builder().userAId(a).userBId(b).build());
                });
    }

    private void requireCanInitiateConversation(String senderId, String recipientId) {
        if (userBlockRepository.existsBetween(senderId, recipientId)) {
            throw new ForbiddenException("You can't start a conversation with this user");
        }
        if (!privacySettingsService.canMessage(recipientId, isConnectedForMessaging(senderId, recipientId))) {
            throw new ForbiddenException("This user only accepts messages from their connections");
        }
    }

    // An accepted opportunity application, shared active startup team membership, or an accepted
    // investor/founder introduction counts the same as a connection for messaging purposes only —
    // none of these create a real Connection or affect mutual-connections/graph matching anywhere
    // else. This lets an accepted applicant/teammate/intro and the other party message each other
    // even when the recipient's message permission is set to "connections only".
    private boolean isConnectedForMessaging(String senderId, String recipientId) {
        return connectionRepository.existsAcceptedBetween(senderId, recipientId)
                || opportunityApplicantRepository.existsAcceptedApplicationBetween(senderId, recipientId)
                || startupTeamMemberRepository.existsActiveTeamMembershipBetween(senderId, recipientId)
                || introRequestRepository.existsAcceptedIntroBetween(senderId, recipientId);
    }

    @Transactional(readOnly = true)
    public Page<ConversationDto> list(String viewerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return conversationRepository.findVisibleForViewer(viewerId, pageable)
                .map(c -> toDto(c, viewerId));
    }

    @Transactional(readOnly = true)
    public Page<MessageDto> getMessages(String conversationId, String viewerId, int page, int size) {
        Conversation conversation = getConversationForParticipant(conversationId, viewerId);
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findVisibleForViewer(conversation.getId(), viewerId, pageable)
                .map(m -> toMessageDto(m, viewerId));
    }

    @Transactional
    public MessageDto sendMessage(String conversationId, String senderId, String content, String sharedPostId) {
        Conversation conversation = getConversationForParticipant(conversationId, senderId);
        String recipientId = conversation.otherParticipant(senderId);
        if (userBlockRepository.existsBetween(senderId, recipientId)) {
            throw new ForbiddenException("You can't send messages in this conversation");
        }
        if (!privacySettingsService.canMessage(recipientId, isConnectedForMessaging(senderId, recipientId))) {
            throw new ForbiddenException("This user only accepts messages from their connections");
        }

        String trimmedContent = content == null ? "" : content.trim();
        String normalizedPostId = (sharedPostId == null || sharedPostId.isBlank()) ? null : sharedPostId;
        if (trimmedContent.isEmpty() && normalizedPostId == null) {
            throw new BadRequestException("Message must have content or a shared post");
        }
        if (normalizedPostId != null) {
            // Fail fast (404) if the post doesn't exist rather than persisting a dangling reference.
            feedService.get(senderId, normalizedPostId);
        }

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .contentCiphertext(encryptionService.encrypt(trimmedContent))
                .messageType(normalizedPostId != null ? Message.Type.SHARED_POST : Message.Type.TEXT)
                .sharedPostId(normalizedPostId)
                .build();
        message = messageRepository.saveAndFlush(message);

        // Bumps updated_at so the conversation resurfaces at the top of both participants' lists
        // (and past either side's deletedAtFor, if they'd previously deleted the chat).
        Instant now = Instant.now();
        conversationRepository.touchUpdatedAt(conversation.getId(), now);
        conversation.setUpdatedAt(now);

        MessageDto dto = toMessageDto(message, senderId);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), dto);
        messagingTemplate.convertAndSend("/topic/users/" + recipientId + "/conversations",
                toDto(conversation, recipientId));
        return dto;
    }

    @Transactional
    public void markRead(String conversationId, String viewerId) {
        Conversation conversation = getConversationForParticipant(conversationId, viewerId);
        int updated = messageRepository.markConversationRead(conversation.getId(), viewerId);
        if (updated > 0) {
            messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId() + "/read",
                    new ReadReceipt(viewerId));
        }
    }

    @Transactional
    public ConversationDto toggleMute(String conversationId, String viewerId) {
        Conversation conversation = getConversationForParticipant(conversationId, viewerId);
        conversation.setMutedFor(viewerId, !conversation.isMutedFor(viewerId));
        conversationRepository.save(conversation);
        return toDto(conversation, viewerId);
    }

    @Transactional
    public ConversationDto setNickname(String conversationId, String viewerId, String nickname) {
        Conversation conversation = getConversationForParticipant(conversationId, viewerId);
        conversation.setNicknameFor(viewerId, (nickname == null || nickname.isBlank()) ? null : nickname.trim());
        conversationRepository.save(conversation);
        return toDto(conversation, viewerId);
    }

    /**
     * "Delete chat" is per-viewer only: the conversation is hidden from the viewer's inbox
     * (deletedAtFor) and every message that currently exists is individually hidden from the
     * viewer via a MessageDeletion row. The shared Message/Conversation rows are never touched,
     * so the other participant's history is completely unaffected. Messages sent after this point
     * have no deletion row for the viewer and are visible as normal.
     */
    @Transactional
    public void deleteConversation(String conversationId, String viewerId) {
        Conversation conversation = getConversationForParticipant(conversationId, viewerId);
        conversation.setDeletedAtFor(viewerId, Instant.now());
        conversationRepository.save(conversation);

        List<String> messageIds = messageRepository.findByConversationId(conversation.getId()).stream()
                .map(Message::getId)
                .toList();
        if (messageIds.isEmpty()) return;

        Set<String> alreadyDeleted = messageDeletionRepository.findDeletedMessageIds(viewerId, messageIds);
        List<MessageDeletion> newDeletions = messageIds.stream()
                .filter(id -> !alreadyDeleted.contains(id))
                .map(id -> MessageDeletion.builder().messageId(id).userId(viewerId).build())
                .toList();
        messageDeletionRepository.saveAll(newDeletions);
    }

    private Conversation getConversationForParticipant(String conversationId, String viewerId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
        if (!conversation.hasParticipant(viewerId)) {
            throw new ForbiddenException("You are not a participant in this conversation");
        }
        return conversation;
    }

    private ConversationDto toDto(Conversation conversation, String viewerId) {
        List<Message> latest = messageRepository.findLatestVisibleForViewer(conversation.getId(), viewerId, PageRequest.of(0, 1));
        MessageDto lastMessage = latest.isEmpty() ? null : toMessageDto(latest.get(0), viewerId);
        long unread = messageRepository.countUnreadVisibleForViewer(conversation.getId(), viewerId);
        String otherId = conversation.otherParticipant(viewerId);
        boolean blocked = userBlockRepository.existsBetween(viewerId, otherId);
        return new ConversationDto(conversation.getId(), otherId, lastMessage, unread, conversation.getUpdatedAt(),
                conversation.isMutedFor(viewerId), conversation.nicknameFor(viewerId), blocked);
    }

    private record ReadReceipt(String readBy) {}

    private MessageDto toMessageDto(Message message, String viewerId) {
        PostDto sharedPost = null;
        if (message.getMessageType() == Message.Type.SHARED_POST && message.getSharedPostId() != null) {
            try {
                sharedPost = feedService.get(viewerId, message.getSharedPostId());
            } catch (ResourceNotFoundException ignored) {
                // Post was deleted after being shared; frontend shows a "no longer available" state.
            }
        }
        return new MessageDto(message.getId(), message.getConversationId(), message.getSenderId(),
                message.getMessageType().name(), encryptionService.decrypt(message.getContentCiphertext()),
                message.getSharedPostId(), sharedPost, message.isRead(), message.getCreatedAt());
    }
}
