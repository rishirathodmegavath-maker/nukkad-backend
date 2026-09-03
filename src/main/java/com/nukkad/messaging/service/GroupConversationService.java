package com.nukkad.messaging.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.messaging.dto.ConversationDto;
import com.nukkad.messaging.entity.Conversation;
import com.nukkad.messaging.entity.ConversationParticipant;
import com.nukkad.messaging.repository.ConversationParticipantRepository;
import com.nukkad.messaging.repository.ConversationRepository;
import com.nukkad.user.repository.ConnectionRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConnectionRepository connectionRepository;
    private final FileStorageService fileStorageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationService conversationService;

    public GroupConversationService(ConversationRepository conversationRepository,
                                     ConversationParticipantRepository participantRepository,
                                     ConnectionRepository connectionRepository,
                                     FileStorageService fileStorageService,
                                     SimpMessagingTemplate messagingTemplate,
                                     ConversationService conversationService) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.connectionRepository = connectionRepository;
        this.fileStorageService = fileStorageService;
        this.messagingTemplate = messagingTemplate;
        this.conversationService = conversationService;
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
        for (String memberId : memberIds) {
            if (memberId.equals(viewerId)) continue;
            var existing = participantRepository.findByConversationIdAndUserId(conversationId, memberId);
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
        broadcastGroupUpdate(conversation);
        return conversationService.toDto(conversation, viewerId);
    }

    @Transactional
    public void leaveGroup(String viewerId, String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .filter(c -> c.getConversationType() == Conversation.Type.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + conversationId));
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, viewerId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this group"));

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
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + conversationId));
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, viewerId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this group"));
        if (participant.getRole() != ConversationParticipant.Role.ADMIN) {
            throw new ForbiddenException("Only group admins can do this");
        }
        return conversation;
    }

    private void broadcastGroupUpdate(Conversation conversation) {
        for (ConversationParticipant p : participantRepository.findByConversationIdAndDeletedAtIsNull(conversation.getId())) {
            messagingTemplate.convertAndSend("/topic/users/" + p.getUserId() + "/conversations",
                    conversationService.toDto(conversation, p.getUserId()));
        }
    }
}
