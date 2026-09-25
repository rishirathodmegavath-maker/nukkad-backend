package com.nukkad.messaging.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.paging.PageRequests;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.feed.dto.PostDto;
import com.nukkad.feed.service.FeedService;
import com.nukkad.investor.repository.IntroRequestRepository;
import com.nukkad.messaging.dto.ConversationAttachmentRef;
import com.nukkad.messaging.dto.ConversationDto;
import com.nukkad.messaging.dto.GroupInfoDto;
import com.nukkad.messaging.dto.GroupParticipantDto;
import com.nukkad.messaging.dto.MessageAttachmentDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    /** How long a chat attachment's presigned URL stays valid. Short enough that a leaked URL (screenshot,
     * proxy log, forwarded link) is a narrow window rather than a standing door; long enough to open,
     * play and download comfortably. It used to be 6 hours only because a stale URL had no way to be
     * refreshed — now an expired one is re-minted on demand ({@link #getAttachment}), so the window can be
     * this small without an open tab ever being stuck with a broken image. */
    private static final Duration ATTACHMENT_URL_TTL = Duration.ofHours(1);

    /** One message for every reason an attachment reference is refused (not this conversation's, already
     * used, not in storage) so the response never says which — or whether a given key exists. */
    private static final String INVALID_ATTACHMENT = "This attachment can't be sent. Please upload it again.";
    /** Same wording for "no such conversation" and "you're not in it": a non-participant can't tell them apart. */
    private static final String CONVERSATION_NOT_FOUND = "Conversation not found";
    private static final String ATTACHMENT_NOT_FOUND = "Attachment not found";

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
    private final FileStorageService fileStorageService;

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
                                FeedService feedService,
                                FileStorageService fileStorageService) {
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
        this.fileStorageService = fileStorageService;
    }

    /** Uploads a chat attachment for {@code conversationId} — a participant-only action, exactly like
     * sending a message itself. Returns a ref (the private object key, not a URL) the caller then passes
     * to {@link #sendMessage} to actually create the message; uploading alone never creates one. Unlike a
     * feed post's attachment, this is stored privately and its declared type is cross-checked against its
     * actual bytes (see {@link FileStorageService#storeConversationAttachment}). */
    @Transactional(readOnly = true)
    public ConversationAttachmentRef uploadAttachment(String conversationId, String userId, MultipartFile file) {
        Conversation conversation = getConversationForParticipant(conversationId, userId);
        // The conversation id is baked into the key's path, which is what lets sendMessage later prove the
        // key belongs to the conversation it's being attached to (see FileStorageService#isConversationAttachmentKey).
        var stored = fileStorageService.storeConversationAttachment(file, "messages/" + conversation.getId());
        return new ConversationAttachmentRef(stored.key(), stored.kind().name(),
                FileStorageService.safeDisplayName(file.getOriginalFilename()));
    }

    /** What a client is actually allowed to attach, derived entirely server-side from a reference it sent back. */
    private record ValidatedAttachment(String key, FileStorageService.AttachmentKind kind, String fileName) {}

    /**
     * Turns a client-supplied attachment reference into one the server will stand behind. The key is data the
     * client controls, and it ends up (a) presigned into a download URL for every participant and (b) deleted
     * from object storage on unsend — so accepting an arbitrary one would let any member read any object in
     * the bucket by key and delete objects that aren't theirs. Hence all of: the key must have exactly the
     * shape this service minted for THIS conversation (which also excludes every other prefix and any path
     * trickery), it must not already back another message, and it must really exist in storage — whose recorded
     * content type, not the request's {@code kind}, decides the message type.
     */
    private ValidatedAttachment validateAttachment(Conversation conversation, ConversationAttachmentRef ref) {
        String key = ref.key();
        if (!FileStorageService.isConversationAttachmentKey(key, conversation.getId())
                || messageRepository.existsByAttachmentKey(key)) {
            throw new BadRequestException(INVALID_ATTACHMENT);
        }
        FileStorageService.AttachmentKind kind = fileStorageService.describePrivateObject(key)
                .orElseThrow(() -> new BadRequestException(INVALID_ATTACHMENT));
        return new ValidatedAttachment(key, kind, FileStorageService.safeDisplayName(ref.fileName()));
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
        return new PageImpl<>(content, PageRequests.of(page, size), all.size());
    }

    @Transactional(readOnly = true)
    public Page<MessageDto> getMessages(String conversationId, String viewerId, int page, int size) {
        Conversation conversation = getConversationForParticipant(conversationId, viewerId);
        Pageable pageable = PageRequests.of(page, size);
        return messageRepository.findVisibleForViewer(conversation.getId(), viewerId, pageable)
                .map(m -> toMessageDto(m, viewerId));
    }

    @Transactional
    public MessageDto sendMessage(String conversationId, String senderId, String content, String sharedPostId, String replyToMessageId) {
        return sendMessage(conversationId, senderId, content, sharedPostId, replyToMessageId, null);
    }

    @Transactional
    public MessageDto sendMessage(String conversationId, String senderId, String content, String sharedPostId,
                                   String replyToMessageId, ConversationAttachmentRef attachment) {
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
        if (trimmedContent.isEmpty() && normalizedPostId == null && attachment == null) {
            throw new BadRequestException("Message must have content, an attachment, or a shared post");
        }
        if (normalizedPostId != null) {
            // Fail fast (404) if the post doesn't exist rather than persisting a dangling reference.
            feedService.get(senderId, normalizedPostId);
            feedService.recordShare(senderId, normalizedPostId);
        }
        String normalizedReplyToId = (replyToMessageId == null || replyToMessageId.isBlank()) ? null : replyToMessageId;
        if (normalizedReplyToId != null) {
            messageRepository.findById(normalizedReplyToId)
                    .filter(m -> m.getConversationId().equals(conversation.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + normalizedReplyToId));
        }

        ValidatedAttachment validatedAttachment = attachment != null ? validateAttachment(conversation, attachment) : null;

        Message.Type messageType;
        if (normalizedPostId != null) {
            messageType = Message.Type.SHARED_POST;
        } else if (validatedAttachment != null) {
            messageType = Message.Type.valueOf(validatedAttachment.kind().name());
        } else {
            messageType = Message.Type.TEXT;
        }

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .replyToMessageId(normalizedReplyToId)
                .contentCiphertext(encryptionService.encrypt(trimmedContent))
                .messageType(messageType)
                .sharedPostId(normalizedPostId)
                .attachmentKey(validatedAttachment != null ? validatedAttachment.key() : null)
                .attachmentKind(validatedAttachment != null ? validatedAttachment.kind().name() : null)
                .attachmentFileName(validatedAttachment != null ? validatedAttachment.fileName() : null)
                .build();
        message = messageRepository.saveAndFlush(message);

        // Bumps updated_at so the conversation resurfaces at the top of every participant's list
        // (and past a DIRECT side's deletedAtFor, if they'd previously deleted the chat).
        Instant now = Instant.now();
        conversationRepository.touchUpdatedAt(conversation.getId(), now);
        conversation.setUpdatedAt(now);

        // Deliberately NO persistent notification here. A chat message is not a notification-center
        // event: someone in a busy chat would bury every real notification (connection requests,
        // application updates, ...). Recipients who are online get a short-lived in-app toast from the
        // per-user conversations topic broadcast below; anyone else finds it as an unread conversation
        // in Messages (the unread count on the conversation itself).

        MessageDto dto = toMessageDto(message, senderId);
        // One topic per conversation, fanned out by STOMP to every current subscriber — this line is
        // identical for DIRECT and GROUP and needs no branching. Only the per-recipient sidebar-list
        // refresh below has to loop for a group instead of resolving a single otherParticipant.
        List<ConversationDto> recipientConversationDtos = recipientIds.stream().map(id -> toDto(conversation, id)).toList();

        // Broadcast only once this transaction has actually committed. Broadcasting from inside it (as
        // this used to) let a recipient's client receive the WS push and immediately call mark-as-read
        // — a separate request/transaction — before this message's own insert was durably visible to
        // it, so that UPDATE matched nothing, no read-receipt ever went out, and the sender's status
        // stayed stuck on "Sent" forever (nothing else would re-trigger a later mark-as-read). Confirmed
        // more reproducible on SHARED_POST messages specifically, since resolving the shared post before
        // the insert above widens this exact race window. Falls back to broadcasting immediately when no
        // transaction is actually active (e.g. a plain unit test calling this service directly, bypassing
        // Spring's @Transactional proxy) rather than throwing.
        Runnable broadcast = () -> {
            messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), dto);
            for (int i = 0; i < recipientIds.size(); i++) {
                messagingTemplate.convertAndSend("/topic/users/" + recipientIds.get(i) + "/conversations",
                        recipientConversationDtos.get(i));
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast.run();
                }
            });
        } else {
            broadcast.run();
        }
        return dto;
    }

    /**
     * Edit: only the sender may ever call this (enforced here, never trusted from the client) and
     * only for a TEXT message — a SHARED_POST's, or an IMAGE/VIDEO/PDF/FILE message's, attachment/caption
     * is never editable this way (there's no separate caption-only edit path for an attachment message).
     * Unchanged content (after trim) is a deliberate no-op: nothing is persisted or broadcast, matching
     * "don't make an unnecessary API request".
     */
    @Transactional
    public MessageDto editMessage(String conversationId, String userId, String messageId, String content) {
        Conversation conversation = getConversationForParticipant(conversationId, userId);
        Message message = messageRepository.findById(messageId)
                .filter(m -> m.getConversationId().equals(conversation.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
        if (!message.getSenderId().equals(userId)) {
            throw new ForbiddenException("You can only edit your own messages");
        }
        if (message.getUnsentAt() != null) {
            throw new BadRequestException("This message was unsent");
        }
        if (message.getMessageType() != Message.Type.TEXT) {
            throw new BadRequestException("Only text messages can be edited");
        }
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Message can't be empty");
        }
        String currentContent = encryptionService.decrypt(message.getContentCiphertext());
        if (trimmed.equals(currentContent)) {
            return toMessageDto(message, userId);
        }

        message.setContentCiphertext(encryptionService.encrypt(trimmed));
        message.setEditedAt(Instant.now());
        messageRepository.save(message);

        MessageDto dto = toMessageDto(message, userId);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), dto);
        return dto;
    }

    /**
     * Unsend: global — removes the content for every participant, unlike {@link #hideMessagesForViewer}
     * which only ever changes the caller's own view. Only the sender may call this (enforced here).
     * The row is kept (not hard-deleted) so reply references, ordering and pagination stay intact;
     * the ciphertext is wiped too so the original text is actually gone from storage, and
     * {@link #toMessageDto} returns empty content/no attachment for it from this point on for both
     * sides. Any attachment is actually deleted from object storage too (not just unlinked) — this is
     * the one place that happens, since "delete for me" only ever hides the row for one viewer and must
     * never remove a file the other participant can still see. Calling this twice on an already-unsent
     * message is a harmless no-op.
     */
    @Transactional
    public MessageDto unsendMessage(String conversationId, String userId, String messageId) {
        Conversation conversation = getConversationForParticipant(conversationId, userId);
        Message message = messageRepository.findById(messageId)
                .filter(m -> m.getConversationId().equals(conversation.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
        if (!message.getSenderId().equals(userId)) {
            throw new ForbiddenException("You can only unsend your own messages");
        }
        if (message.getUnsentAt() == null) {
            String attachmentKey = message.getAttachmentKey();
            if (attachmentKey != null) {
                // Belt and braces on top of validateAttachment: only ever delete a key that is shaped like a
                // chat attachment of THIS conversation (or the old flat layout), and only if no other message
                // still shows the same file. A row that doesn't qualify is simply unlinked below.
                boolean ownedChatFile = FileStorageService.isConversationAttachmentKey(attachmentKey, conversation.getId())
                        || FileStorageService.isLegacyConversationAttachmentKey(attachmentKey);
                if (ownedChatFile && !messageRepository.existsByAttachmentKeyAndIdNot(attachmentKey, message.getId())) {
                    fileStorageService.deleteByKey(attachmentKey);
                } else {
                    log.warn("Unsending message {} without deleting its stored file: key is shared or outside this conversation's chat-attachment layout",
                            message.getId());
                }
            }
            message.setUnsentAt(Instant.now());
            message.setContentCiphertext(encryptionService.encrypt(""));
            message.setAttachmentKey(null);
            message.setAttachmentKind(null);
            message.setAttachmentFileName(null);
            messageRepository.save(message);
        }

        MessageDto dto = toMessageDto(message, userId);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), dto);
        return dto;
    }

    @Transactional
    public void markRead(String conversationId, String viewerId) {
        Conversation conversation = getConversationForParticipant(conversationId, viewerId);
        Instant now = Instant.now();
        if (conversation.getConversationType() == Conversation.Type.GROUP) {
            // Per-user read state (last_read_at) rather than the shared is_read boolean, which only
            // makes sense for exactly 2 participants.
            ConversationParticipant participant = participantRepository
                    .findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, viewerId)
                    .orElseThrow(() -> new ForbiddenException("You are not a participant in this conversation"));
            participant.setLastReadAt(now);
            participantRepository.save(participant);
            messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId() + "/read", new ReadReceipt(viewerId, now));
            return;
        }
        int updated = messageRepository.markConversationRead(conversation.getId(), viewerId, now);
        if (updated > 0) {
            messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId() + "/read",
                    new ReadReceipt(viewerId, now));
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

    /** The single gate for every conversation-scoped operation. A conversation the caller isn't in is reported
     * exactly like one that doesn't exist (404, same message, id not echoed) — a 403 here would let anyone
     * enumerate which conversation ids are real by trying them. */
    private Conversation getConversationForParticipant(String conversationId, String viewerId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(CONVERSATION_NOT_FOUND));
        boolean allowed = conversation.getConversationType() == Conversation.Type.GROUP
                ? participantRepository.existsByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, viewerId)
                : conversation.hasParticipant(viewerId);
        if (!allowed) {
            throw new ResourceNotFoundException(CONVERSATION_NOT_FOUND);
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

    private record ReadReceipt(String readBy, Instant readAt) {}

    private MessageDto toMessageDto(Message message, String viewerId) {
        // Unsent: content/attachment are gone for everyone from here on — nothing left to decrypt or
        // resolve, and no reply-to preview on the tombstone itself (there's nothing left to quote).
        if (message.getUnsentAt() != null) {
            return new MessageDto(message.getId(), message.getConversationId(), message.getSenderId(),
                    message.getMessageType().name(), "", null, null, null, message.getReplyToMessageId(), null,
                    message.isRead(), message.getReadAt(), message.getEditedAt(), message.getUnsentAt(), message.getCreatedAt());
        }

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
                        // Recomputed fresh on every call, never a stored snapshot — an edit to the
                        // original is reflected the next time this replying message's DTO is built,
                        // and an unsent original degrades gracefully instead of leaking its old text.
                        String snippet;
                        if (original.getUnsentAt() != null) {
                            snippet = "This message was unsent";
                        } else if (original.getMessageType() == Message.Type.SHARED_POST) {
                            snippet = "Shared a post";
                        } else if (isAttachmentType(original.getMessageType())) {
                            String caption = encryptionService.decrypt(original.getContentCiphertext());
                            snippet = !caption.isBlank() ? truncate(caption, 120) : attachmentTypeLabel(original);
                        } else {
                            snippet = truncate(encryptionService.decrypt(original.getContentCiphertext()), 120);
                        }
                        return new RepliedMessagePreviewDto(original.getId(), original.getSenderId(),
                                original.getMessageType().name(), snippet);
                    })
                    .orElse(null); // Original was hard-deleted; frontend just omits the quoted preview.
        }
        // Presigned fresh on every DTO build — REST page fetch and WebSocket broadcast both go through
        // this same method, so both always carry a currently-valid URL regardless of how long the
        // underlying message has existed. Presigning is a local signature computation (no request to
        // storage), so doing this per-message on every read is cheap.
        MessageAttachmentDto attachment = message.getAttachmentKey() != null ? presignedAttachment(message) : null;
        return new MessageDto(message.getId(), message.getConversationId(), message.getSenderId(),
                message.getMessageType().name(), encryptionService.decrypt(message.getContentCiphertext()),
                message.getSharedPostId(), sharedPost, attachment, message.getReplyToMessageId(), replyTo,
                message.isRead(), message.getReadAt(), message.getEditedAt(), message.getUnsentAt(), message.getCreatedAt());
    }

    /** Builds the attachment DTO with a freshly presigned GET URL — but only for a key that has the shape of a
     * chat attachment of this message's own conversation (or the old flat layout). Anything else (a row that
     * predates key validation and points somewhere it shouldn't) gets no URL at all rather than a signature
     * for whatever object the key names; the client shows its "unavailable" state. */
    private MessageAttachmentDto presignedAttachment(Message message) {
        String key = message.getAttachmentKey();
        boolean presignable = FileStorageService.isConversationAttachmentKey(key, message.getConversationId())
                || FileStorageService.isLegacyConversationAttachmentKey(key);
        if (!presignable) {
            log.warn("Not presigning the attachment of message {}: its key is outside the chat-attachment layout", message.getId());
        }
        String url = presignable ? fileStorageService.presignGet(key, ATTACHMENT_URL_TTL) : null;
        return new MessageAttachmentDto(url, message.getAttachmentKind(), message.getAttachmentFileName());
    }

    /**
     * A fresh presigned URL for one message's attachment, for a client whose earlier URL expired (or that never
     * had one). This is the ONLY way a URL is minted other than as part of a message the caller may already read,
     * and it takes no key from the client at all — just ids, resolved server-side through the whole chain:
     * authenticated caller → caller is a participant of the conversation → the message is in that conversation
     * (an id from any other conversation is indistinguishable from a missing one) → the caller hasn't hidden it
     * ("delete for me") → it hasn't been unsent → it really has an attachment → the key is a chat-attachment key.
     * Every failure is the same 404 so nothing about other conversations, messages or objects can be probed.
     */
    @Transactional(readOnly = true)
    public MessageAttachmentDto getAttachment(String conversationId, String viewerId, String messageId) {
        Conversation conversation = getConversationForParticipant(conversationId, viewerId);
        Message message = messageRepository.findById(messageId)
                .filter(m -> m.getConversationId().equals(conversation.getId()))
                .filter(m -> m.getUnsentAt() == null && m.getAttachmentKey() != null)
                .filter(m -> messageDeletionRepository.findDeletedMessageIds(viewerId, List.of(m.getId())).isEmpty())
                .orElseThrow(() -> new ResourceNotFoundException(ATTACHMENT_NOT_FOUND));
        MessageAttachmentDto attachment = presignedAttachment(message);
        if (attachment.url() == null) {
            throw new ResourceNotFoundException(ATTACHMENT_NOT_FOUND);
        }
        return attachment;
    }

    private static boolean isAttachmentType(Message.Type type) {
        return type == Message.Type.IMAGE || type == Message.Type.VIDEO || type == Message.Type.PDF || type == Message.Type.FILE;
    }

    /** A caption-less attachment message still needs something to show as its reply-quote/list-preview
     * text — "Photo", "Video", or the stored file name for a document. */
    private static String attachmentTypeLabel(Message message) {
        return switch (message.getMessageType()) {
            case IMAGE -> "Photo";
            case VIDEO -> "Video";
            case PDF, FILE -> message.getAttachmentFileName() != null ? message.getAttachmentFileName() : "File";
            default -> "";
        };
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }
}
