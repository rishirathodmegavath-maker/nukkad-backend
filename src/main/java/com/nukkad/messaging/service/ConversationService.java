package com.nukkad.messaging.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.feed.dto.PostDto;
import com.nukkad.feed.service.FeedService;
import com.nukkad.investor.repository.IntroRequestRepository;
import com.nukkad.messaging.dto.ConversationDto;
import com.nukkad.messaging.dto.GroupInfoDto;
import com.nukkad.messaging.dto.GroupParticipantDto;
import com.nukkad.messaging.dto.MessageDto;
import com.nukkad.messaging.dto.RepliedMessagePreviewDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
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
                                ConversationParticipantRepository participantRepository,
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
        this.participantRepository = participantRepository;
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
        // DIRECT + GROUP conversations live in the same table but have no shared "visible to viewer"
        // query (GROUP membership lives in a separate join table) — merged and paginated here in
        // Java rather than a UNION query, which fits this codebase's plain-JPQL style and is fine at
        // this app's scale.
        List<Conversation> direct = conversationRepository.findVisibleForViewer(viewerId, Pageable.unpaged()).getContent();
        List<String> groupIds = participantRepository.findVisibleGroupConversationIdsForUser(viewerId);
        List<Conversation> groups = groupIds.isEmpty() ? List.of() : conversationRepository.findAllById(groupIds);

        List<Conversation> all = new ArrayList<>(direct.size() + groups.size());
        all.addAll(direct);
        all.addAll(groups);
        all.sort(Comparator.comparing(Conversation::getUpdatedAt).reversed());

        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<ConversationDto> content = all.subList(from, to).stream().map(c -> toDto(c, viewerId)).toList();
        return new PageImpl<>(content, PageRequest.of(page, size), all.size());
    }

    @Transactional(readOnly = true)
    public Page<MessageDto> getMessages(String conversationId, String viewerId, int page, int size) {
        Conversation conversation = getConversationForParticipant(conversationId, viewerId);
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findVisibleForViewer(conversation.getId(), viewerId, pageable)
                .map(m -> toMessageDto(m, viewerId));
    }

    @Transactional
    public MessageDto sendMessage(String conversationId, String senderId, String content, String sharedPostId, String replyToMessageId) {
        Conversation conversation = getConversationForParticipant(conversationId, senderId);
        boolean isGroup = conversation.getConversationType() == Conversation.Type.GROUP;

        List<String> recipientIds;
        if (isGroup) {
            // No per-pair block/privacy gate for groups in v1 — a documented limitation. Anyone still
            // an active participant receives the message.
            recipientIds = participantRepository.findByConversationIdAndDeletedAtIsNull(conversation.getId()).stream()
                    .map(ConversationParticipant::getUserId)
                    .filter(id -> !id.equals(senderId))
                    .toList();
        } else {
            String recipientId = conversation.otherParticipant(senderId);
            if (userBlockRepository.existsBetween(senderId, recipientId)) {
                throw new ForbiddenException("You can't send messages in this conversation");
            }
            if (!privacySettingsService.canMessage(recipientId, isConnectedForMessaging(senderId, recipientId))) {
                throw new ForbiddenException("This user only accepts messages from their connections");
            }
            recipientIds = List.of(recipientId);
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
        String normalizedReplyToId = (replyToMessageId == null || replyToMessageId.isBlank()) ? null : replyToMessageId;
        if (normalizedReplyToId != null) {
            messageRepository.findById(normalizedReplyToId)
                    .filter(m -> m.getConversationId().equals(conversation.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + normalizedReplyToId));
        }

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .replyToMessageId(normalizedReplyToId)
                .contentCiphertext(encryptionService.encrypt(trimmedContent))
                .messageType(normalizedPostId != null ? Message.Type.SHARED_POST : Message.Type.TEXT)
                .sharedPostId(normalizedPostId)
                .build();
        message = messageRepository.saveAndFlush(message);

        // Bumps updated_at so the conversation resurfaces at the top of every participant's list
        // (and past a DIRECT side's deletedAtFor, if they'd previously deleted the chat).
        Instant now = Instant.now();
        conversationRepository.touchUpdatedAt(conversation.getId(), now);
        conversation.setUpdatedAt(now);

        MessageDto dto = toMessageDto(message, senderId);
        // One topic per conversation, fanned out by STOMP to every current subscriber — this line is
        // identical for DIRECT and GROUP and needs no branching. Only the per-recipient sidebar-list
        // refresh below has to loop for a group instead of resolving a single otherParticipant.
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), dto);
        for (String recipientId : recipientIds) {
            messagingTemplate.convertAndSend("/topic/users/" + recipientId + "/conversations",
                    toDto(conversation, recipientId));
        }
        return dto;
    }

    @Transactional
    public void markRead(String conversationId, String viewerId) {
        Conversation conversation = getConversationForParticipant(conversationId, viewerId);
        if (conversation.getConversationType() == Conversation.Type.GROUP) {
            // Per-user read state (last_read_at) rather than the shared is_read boolean, which only
            // makes sense for exactly 2 participants.
            ConversationParticipant participant = participantRepository
                    .findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, viewerId)
                    .orElseThrow(() -> new ForbiddenException("You are not a participant in this conversation"));
            participant.setLastReadAt(Instant.now());
            participantRepository.save(participant);
            messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId() + "/read", new ReadReceipt(viewerId));
            return;
        }
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

    /**
     * "Delete for me": hides the given messages from {@code viewerId}'s own view only, via the same
     * per-viewer MessageDeletion mechanism {@link #deleteConversation} uses for the whole chat. The
     * shared Message row is never touched, so the other participant's history — and, since
     * {@link com.nukkad.messaging.repository.MessageRepository}'s viewer-scoped queries are the only
     * read path, their unread count and "last message" preview too — is completely unaffected. Any
     * participant may hide any message in the conversation this way, including ones sent by the
     * other participant, since it only ever changes the caller's own visibility. Every id must exist
     * and belong to this conversation — a single invalid id fails the whole batch (all-or-nothing) so
     * a malicious client can't probe for other conversations' message ids. Re-hiding an
     * already-hidden message is a harmless no-op. No realtime event is broadcast: only the acting
     * viewer's own view changes, and their own client already reflects that from this call's result.
     */
    @Transactional
    public void hideMessagesForViewer(String conversationId, String viewerId, List<String> messageIds) {
        if (messageIds.isEmpty()) {
            throw new BadRequestException("No messages specified");
        }
        Conversation conversation = getConversationForParticipant(conversationId, viewerId);

        List<Message> messages = messageRepository.findAllById(messageIds);
        if (messages.size() != messageIds.size()) {
            throw new ResourceNotFoundException("One or more messages were not found");
        }
        for (Message message : messages) {
            if (!message.getConversationId().equals(conversation.getId())) {
                throw new ResourceNotFoundException("One or more messages were not found");
            }
        }

        Set<String> alreadyHidden = messageDeletionRepository.findDeletedMessageIds(viewerId, messageIds);
        List<MessageDeletion> newHides = messageIds.stream()
                .filter(id -> !alreadyHidden.contains(id))
                .map(id -> MessageDeletion.builder().messageId(id).userId(viewerId).build())
                .toList();
        messageDeletionRepository.saveAll(newHides);
    }

    private Conversation getConversationForParticipant(String conversationId, String viewerId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
        boolean allowed = conversation.getConversationType() == Conversation.Type.GROUP
                ? participantRepository.existsByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, viewerId)
                : conversation.hasParticipant(viewerId);
        if (!allowed) {
            throw new ForbiddenException("You are not a participant in this conversation");
        }
        return conversation;
    }

    /** Package-private (not private) so {@link GroupConversationService} can build the same DTO
     * shape after group-membership mutations, instead of duplicating this logic. */
    ConversationDto toDto(Conversation conversation, String viewerId) {
        List<Message> latest = messageRepository.findLatestVisibleForViewer(conversation.getId(), viewerId, PageRequest.of(0, 1));
        MessageDto lastMessage = latest.isEmpty() ? null : toMessageDto(latest.get(0), viewerId);

        if (conversation.getConversationType() == Conversation.Type.GROUP) {
            ConversationParticipant me = participantRepository
                    .findByConversationIdAndUserIdAndDeletedAtIsNull(conversation.getId(), viewerId)
                    .orElseThrow(() -> new ForbiddenException("You are not a participant in this conversation"));
            long unread = messageRepository.countUnreadSinceForViewer(conversation.getId(), viewerId, me.getLastReadAt());
            List<GroupParticipantDto> participants = participantRepository
                    .findByConversationIdAndDeletedAtIsNull(conversation.getId()).stream()
                    .map(p -> new GroupParticipantDto(p.getUserId(), p.getRole().name()))
                    .toList();
            GroupInfoDto groupInfo = new GroupInfoDto(conversation.getGroupName(), conversation.getGroupAvatarUrl(),
                    conversation.getCreatedBy(), me.getRole().name(), participants);
            return new ConversationDto(conversation.getId(), conversation.getConversationType().name(), null, groupInfo,
                    lastMessage, unread, conversation.getUpdatedAt(), me.getMutedAt() != null, me.getNickname(), false);
        }

        long unread = messageRepository.countUnreadVisibleForViewer(conversation.getId(), viewerId);
        String otherId = conversation.otherParticipant(viewerId);
        boolean blocked = userBlockRepository.existsBetween(viewerId, otherId);
        return new ConversationDto(conversation.getId(), conversation.getConversationType().name(), otherId, null,
                lastMessage, unread, conversation.getUpdatedAt(), conversation.isMutedFor(viewerId),
                conversation.nicknameFor(viewerId), blocked);
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
        RepliedMessagePreviewDto replyTo = null;
        if (message.getReplyToMessageId() != null) {
            replyTo = messageRepository.findById(message.getReplyToMessageId())
                    .map(original -> {
                        String snippet = original.getMessageType() == Message.Type.SHARED_POST
                                ? "Shared a post"
                                : truncate(encryptionService.decrypt(original.getContentCiphertext()), 120);
                        return new RepliedMessagePreviewDto(original.getId(), original.getSenderId(),
                                original.getMessageType().name(), snippet);
                    })
                    .orElse(null); // Original was hard-deleted; frontend just omits the quoted preview.
        }
        return new MessageDto(message.getId(), message.getConversationId(), message.getSenderId(),
                message.getMessageType().name(), encryptionService.decrypt(message.getContentCiphertext()),
                message.getSharedPostId(), sharedPost, message.getReplyToMessageId(), replyTo,
                message.isRead(), message.getCreatedAt());
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }
}
