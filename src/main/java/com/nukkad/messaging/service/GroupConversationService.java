package com.nukkad.messaging.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.messaging.config.ConversationSubscriptionRevoker;
import com.nukkad.messaging.dto.ConversationDto;
import com.nukkad.messaging.entity.Conversation;
import com.nukkad.messaging.entity.ConversationParticipant;
import com.nukkad.messaging.repository.ConversationParticipantRepository;
import com.nukkad.messaging.repository.ConversationRepository;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserBlockRepository;
import com.nukkad.user.service.UserPrivacySettingsService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * All group-membership logic (create/rename/photo/add/remove/leave/roles) is kept out of
 * {@link ConversationService} on purpose — that service is on the DM-critical path and this keeps
 * it from being touched or risked by membership changes.
 */
@Service
public class GroupConversationService {

    /** Same wording for "no such group" and "you're not in it" — see ConversationService#getConversationForParticipant. */
    private static final String GROUP_NOT_FOUND = "Group not found";

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConnectionRepository connectionRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserPrivacySettingsService privacySettingsService;
    private final FileStorageService fileStorageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationService conversationService;
    private final ConversationSubscriptionRevoker subscriptionRevoker;

    public GroupConversationService(ConversationRepository conversationRepository,
                                     ConversationParticipantRepository participantRepository,
                                     ConnectionRepository connectionRepository,
                                     UserBlockRepository userBlockRepository,
                                     UserPrivacySettingsService privacySettingsService,
                                     FileStorageService fileStorageService,
                                     SimpMessagingTemplate messagingTemplate,
                                     ConversationService conversationService,
                                     ConversationSubscriptionRevoker subscriptionRevoker) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.connectionRepository = connectionRepository;
        this.userBlockRepository = userBlockRepository;
        this.privacySettingsService = privacySettingsService;
        this.fileStorageService = fileStorageService;
        this.messagingTemplate = messagingTemplate;
        this.conversationService = conversationService;
        this.subscriptionRevoker = subscriptionRevoker;
    }

    /** The 1:1 equivalent (ConversationService#requireCanInitiateConversation) only ever checks the
     *  recipient's own setting, because only the sender initiates a DM. A group is many-to-many —
     *  once two people share a group, either can message the other — so both directions' block and
     *  privacy state matter here, not just the adder's relationship to the person being added. */
    private void requireCanShareGroupWith(String a, String b) {
        if (userBlockRepository.existsBetween(a, b)) {
            throw new ForbiddenException("Can't add this member to the group");
        }
        boolean connected = connectionRepository.existsAcceptedBetween(a, b);
        if (!privacySettingsService.canMessage(a, connected) || !privacySettingsService.canMessage(b, connected)) {
            throw new ForbiddenException("Can't add this member to the group");
        }
    }

    @Transactional
    public ConversationDto createGroup(String creatorId, String name, List<String> memberIds) {
        Set<String> members = new LinkedHashSet<>(memberIds);
        members.remove(creatorId);
        if (members.isEmpty()) {
            throw new BadRequestException("A group needs at least one other member");
        }
        for (String memberId : members) {
            if (!connectionRepository.existsAcceptedBetween(creatorId, memberId)) {
                throw new ForbiddenException("You can only add your connections to a group");
            }
        }
        // The creator↔member pairs above are already safe (an accepted connection rules out a block,
        // and canMessage is always true once connected) — but two members being added together may
        // have no relationship with each other at all, and that pairing was never checked before.
        List<String> memberList = new ArrayList<>(members);
        for (int i = 0; i < memberList.size(); i++) {
            for (int j = i + 1; j < memberList.size(); j++) {
                requireCanShareGroupWith(memberList.get(i), memberList.get(j));
            }
        }

        Conversation conversation = conversationRepository.saveAndFlush(Conversation.builder()
                .conversationType(Conversation.Type.GROUP)
                .groupName(name.trim())
                .createdBy(creatorId)
                .build());

        List<ConversationParticipant> participants = new ArrayList<>();
        participants.add(ConversationParticipant.builder()
                .conversationId(conversation.getId()).userId(creatorId).role(ConversationParticipant.Role.ADMIN).build());
        for (String memberId : members) {
            participants.add(ConversationParticipant.builder()
                    .conversationId(conversation.getId()).userId(memberId).role(ConversationParticipant.Role.MEMBER).build());
        }
        participantRepository.saveAll(participants);

        for (String memberId : members) {
            messagingTemplate.convertAndSend("/topic/users/" + memberId + "/conversations",
                    conversationService.toDto(conversation, memberId));
        }
        return conversationService.toDto(conversation, creatorId);
    }

    @Transactional
    public ConversationDto renameGroup(String viewerId, String conversationId, String newName) {
        Conversation conversation = requireGroupAdmin(viewerId, conversationId);
        conversation.setGroupName(newName.trim());
        conversationRepository.save(conversation);
        broadcastGroupUpdate(conversation);
        return conversationService.toDto(conversation, viewerId);
    }

    @Transactional
    public ConversationDto setGroupAvatar(String viewerId, String conversationId, MultipartFile file) {
        Conversation conversation = requireGroupAdmin(viewerId, conversationId);
        conversation.setGroupAvatarUrl(fileStorageService.storeImage(file, "group-avatars"));
        conversationRepository.save(conversation);
        broadcastGroupUpdate(conversation);
        return conversationService.toDto(conversation, viewerId);
    }

    @Transactional
    public ConversationDto addMembers(String viewerId, String conversationId, List<String> memberIds) {
        Conversation conversation = requireGroupAdmin(viewerId, conversationId);
        // Same gap as createGroup's pairwise check, but against whoever is already in the group: the
        // adder↔target check above says nothing about the target's relationship with every OTHER
        // member already sharing this group, who they'd be forced into contact with by joining.
        // Excludes viewerId: the adder↔target pair is already fully covered by the connection check
        // just below (connected ⟹ not blocked, and canMessage is always true once connected), so
        // re-running it here would just repeat that check with swapped arguments for no benefit.
        List<String> otherMemberIds = participantRepository.findByConversationIdAndDeletedAtIsNull(conversationId).stream()
                .map(ConversationParticipant::getUserId)
                .filter(id -> !id.equals(viewerId))
                .toList();
        List<String> newlyActivated = new ArrayList<>();
        for (String memberId : memberIds) {
            if (memberId.equals(viewerId)) continue;
            if (!connectionRepository.existsAcceptedBetween(viewerId, memberId)) {
                throw new ForbiddenException("You can only add your connections to a group");
            }
            var existing = participantRepository.findByConversationIdAndUserId(conversationId, memberId);
            boolean alreadyActive = existing.isPresent() && existing.get().getDeletedAt() == null;
            if (!alreadyActive) {
                for (String otherMemberId : otherMemberIds) {
                    if (!otherMemberId.equals(memberId)) requireCanShareGroupWith(memberId, otherMemberId);
                }
                for (String other : newlyActivated) {
                    requireCanShareGroupWith(memberId, other);
                }
                newlyActivated.add(memberId);
            }
            if (existing.isPresent()) {
                ConversationParticipant participant = existing.get();
                if (participant.getDeletedAt() == null) continue; // already an active member
                participant.setDeletedAt(null);
                participant.setRole(ConversationParticipant.Role.MEMBER);
                participantRepository.save(participant);
            } else {
                participantRepository.save(ConversationParticipant.builder()
                        .conversationId(conversationId).userId(memberId).role(ConversationParticipant.Role.MEMBER).build());
            }
            messagingTemplate.convertAndSend("/topic/users/" + memberId + "/conversations",
                    conversationService.toDto(conversation, memberId));
        }
        broadcastGroupUpdate(conversation);
        return conversationService.toDto(conversation, viewerId);
    }

    @Transactional
    public ConversationDto removeMember(String viewerId, String conversationId, String memberId) {
        Conversation conversation = requireGroupAdmin(viewerId, conversationId);
        if (memberId.equals(viewerId)) {
            throw new BadRequestException("Use leave group to remove yourself");
        }
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        participant.setDeletedAt(Instant.now());
        participantRepository.save(participant);
        revokeRealtimeAccessAfterCommit(memberId, conversationId);
        broadcastGroupUpdate(conversation);
        return conversationService.toDto(conversation, viewerId);
    }

    @Transactional
    public void leaveGroup(String viewerId, String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .filter(c -> c.getConversationType() == Conversation.Type.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException(GROUP_NOT_FOUND));
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, viewerId)
                .orElseThrow(() -> new ResourceNotFoundException(GROUP_NOT_FOUND));

        boolean wasLastAdmin = participant.getRole() == ConversationParticipant.Role.ADMIN
                && participantRepository.countByConversationIdAndRoleAndDeletedAtIsNull(
                        conversationId, ConversationParticipant.Role.ADMIN) == 1;

        participant.setDeletedAt(Instant.now());
        participantRepository.save(participant);

        if (wasLastAdmin) {
            // Auto-promote whoever has been in the group longest so it never ends up admin-less.
            participantRepository.findByConversationIdAndDeletedAtIsNull(conversationId).stream()
                    .min(Comparator.comparing(ConversationParticipant::getJoinedAt))
                    .ifPresent(successor -> {
                        successor.setRole(ConversationParticipant.Role.ADMIN);
                        participantRepository.save(successor);
                    });
        }
        revokeRealtimeAccessAfterCommit(viewerId, conversationId);
        broadcastGroupUpdate(conversation);
    }

    @Transactional
    public ConversationDto updateRole(String viewerId, String conversationId, String targetUserId, String roleValue) {
        Conversation conversation = requireGroupAdmin(viewerId, conversationId);
        ConversationParticipant.Role role;
        try {
            role = ConversationParticipant.Role.valueOf(roleValue);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + roleValue);
        }
        ConversationParticipant target = participantRepository
                .findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + targetUserId));

        if (target.getUserId().equals(viewerId) && role == ConversationParticipant.Role.MEMBER) {
            long adminCount = participantRepository.countByConversationIdAndRoleAndDeletedAtIsNull(
                    conversationId, ConversationParticipant.Role.ADMIN);
            if (adminCount <= 1) {
                throw new BadRequestException("Promote another member to admin first");
            }
        }
        target.setRole(role);
        participantRepository.save(target);
        broadcastGroupUpdate(conversation);
        return conversationService.toDto(conversation, viewerId);
    }

    private Conversation requireGroupAdmin(String viewerId, String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .filter(c -> c.getConversationType() == Conversation.Type.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException(GROUP_NOT_FOUND));
        // A non-member gets the same 404 as a missing group, so group ids can't be enumerated by trying them.
        // Only someone who IS a member (and so can already see the group) is told the reason is "not an admin".
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, viewerId)
                .orElseThrow(() -> new ResourceNotFoundException(GROUP_NOT_FOUND));
        if (participant.getRole() != ConversationParticipant.Role.ADMIN) {
            throw new ForbiddenException("Only group admins can do this");
        }
        return conversation;
    }

    /** Once a member is out, their already-open sockets stop receiving that group's live traffic too — not
     * just their REST access. Deferred to after commit so a rolled-back removal never cuts anyone off. */
    private void revokeRealtimeAccessAfterCommit(String userId, String conversationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    subscriptionRevoker.revoke(userId, conversationId);
                }
            });
        } else {
            subscriptionRevoker.revoke(userId, conversationId);
        }
    }

    private void broadcastGroupUpdate(Conversation conversation) {
        for (ConversationParticipant p : participantRepository.findByConversationIdAndDeletedAtIsNull(conversation.getId())) {
            messagingTemplate.convertAndSend("/topic/users/" + p.getUserId() + "/conversations",
                    conversationService.toDto(conversation, p.getUserId()));
        }
    }
}
