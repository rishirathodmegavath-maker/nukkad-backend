package com.nukkad.messaging.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.messaging.dto.ConversationDto;
import com.nukkad.messaging.entity.Conversation;
import com.nukkad.messaging.entity.ConversationParticipant;
import com.nukkad.messaging.repository.ConversationParticipantRepository;
import com.nukkad.messaging.repository.ConversationRepository;
import com.nukkad.user.repository.ConnectionRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationParticipantRepository participantRepository;
    @Mock private ConnectionRepository connectionRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ConversationService conversationService;

    private GroupConversationService service() {
        return new GroupConversationService(conversationRepository, participantRepository, connectionRepository,
                fileStorageService, messagingTemplate, conversationService);
    }

    private Conversation group() {
        return Conversation.builder().id("conv1").conversationType(Conversation.Type.GROUP).groupName("Old Name").build();
    }

    private ConversationParticipant participant(String userId, ConversationParticipant.Role role, Instant joinedAt) {
        return ConversationParticipant.builder().id(userId + "-p").conversationId("conv1").userId(userId).role(role)
                .joinedAt(joinedAt).build();
    }

    private void stubToDto() {
        when(conversationService.toDto(any(), anyString())).thenReturn(mock(ConversationDto.class));
    }

    @Test
    void creatingAGroupRequiresEveryMemberToBeAnAcceptedConnection() {
        when(connectionRepository.existsAcceptedBetween("alice", "bob")).thenReturn(true);
        when(connectionRepository.existsAcceptedBetween("alice", "mallory")).thenReturn(false);

        assertThatThrownBy(() -> service().createGroup("alice", "Trip Planning", List.of("bob", "mallory")))
                .isInstanceOf(ForbiddenException.class);

        verify(conversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void creatingAGroupMakesTheCreatorAnAdminAndEveryoneElseAMember() {
        when(connectionRepository.existsAcceptedBetween("alice", "bob")).thenReturn(true);
        when(connectionRepository.existsAcceptedBetween("alice", "carol")).thenReturn(true);
        when(conversationRepository.saveAndFlush(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId("conv1");
            return c;
        });
        stubToDto();

        service().createGroup("alice", "Trip Planning", List.of("alice", "bob", "carol"));

        ArgumentCaptor<List<ConversationParticipant>> captor = ArgumentCaptor.forClass(List.class);
        verify(participantRepository).saveAll(captor.capture());
        List<ConversationParticipant> saved = captor.getValue();
        assertThat(saved).hasSize(3); // creator (admin) + bob + carol; "alice" in memberIds is deduped against the creator
        assertThat(saved).filteredOn(p -> p.getUserId().equals("alice")).extracting(ConversationParticipant::getRole)
                .containsExactly(ConversationParticipant.Role.ADMIN);
        assertThat(saved).filteredOn(p -> !p.getUserId().equals("alice"))
                .allMatch(p -> p.getRole() == ConversationParticipant.Role.MEMBER);
    }

    @Test
    void creatingAGroupWithNoOtherMembersIsRejected() {
        assertThatThrownBy(() -> service().createGroup("alice", "Solo Group", List.of("alice")))
                .isInstanceOf(BadRequestException.class);

        verify(conversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void onlyAnAdminCanRenameAGroup() {
        Conversation conv = group();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(participantRepository.findByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "bob"))
                .thenReturn(Optional.of(participant("bob", ConversationParticipant.Role.MEMBER, Instant.now())));

        assertThatThrownBy(() -> service().renameGroup("bob", "conv1", "New Name"))
                .isInstanceOf(ForbiddenException.class);

        assertThat(conv.getGroupName()).isEqualTo("Old Name");
    }

    @Test
    void anAdminCanRenameAGroup() {
        Conversation conv = group();
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(participantRepository.findByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "alice"))
                .thenReturn(Optional.of(participant("alice", ConversationParticipant.Role.ADMIN, Instant.now())));
        when(participantRepository.findByConversationIdAndDeletedAtIsNull("conv1"))
                .thenReturn(List.of(participant("alice", ConversationParticipant.Role.ADMIN, Instant.now())));
        stubToDto();

        service().renameGroup("alice", "conv1", "New Name");

        assertThat(conv.getGroupName()).isEqualTo("New Name");
        verify(conversationRepository).save(conv);
    }

    @Test
    void onlyAnAdminCanRemoveAMember() {
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(group()));
        when(participantRepository.findByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "bob"))
                .thenReturn(Optional.of(participant("bob", ConversationParticipant.Role.MEMBER, Instant.now())));

        assertThatThrownBy(() -> service().removeMember("bob", "conv1", "carol"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aMemberCannotBeRemovedByCallingRemoveMemberOnThemselves() {
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(group()));
        when(participantRepository.findByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "alice"))
                .thenReturn(Optional.of(participant("alice", ConversationParticipant.Role.ADMIN, Instant.now())));

        assertThatThrownBy(() -> service().removeMember("alice", "conv1", "alice"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void leavingAsTheLastAdminAutoPromotesTheLongestStandingMember() {
        Conversation conv = group();
        Instant earlier = Instant.now().minusSeconds(3600);
        Instant later = Instant.now();
        ConversationParticipant admin = participant("alice", ConversationParticipant.Role.ADMIN, later);
        ConversationParticipant longestMember = participant("bob", ConversationParticipant.Role.MEMBER, earlier);
        ConversationParticipant newerMember = participant("carol", ConversationParticipant.Role.MEMBER, later);

        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(participantRepository.findByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "alice")).thenReturn(Optional.of(admin));
        when(participantRepository.countByConversationIdAndRoleAndDeletedAtIsNull("conv1", ConversationParticipant.Role.ADMIN)).thenReturn(1L);
        when(participantRepository.findByConversationIdAndDeletedAtIsNull("conv1")).thenReturn(List.of(longestMember, newerMember));
        stubToDto();

        service().leaveGroup("alice", "conv1");

        assertThat(admin.getDeletedAt()).isNotNull();
        assertThat(longestMember.getRole()).isEqualTo(ConversationParticipant.Role.ADMIN);
        assertThat(newerMember.getRole()).isEqualTo(ConversationParticipant.Role.MEMBER);
    }

    @Test
    void leavingWhenAnotherAdminExistsDoesNotPromoteAnyone() {
        Conversation conv = group();
        ConversationParticipant admin = participant("alice", ConversationParticipant.Role.ADMIN, Instant.now());
        ConversationParticipant otherAdmin = participant("bob", ConversationParticipant.Role.ADMIN, Instant.now());

        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(conv));
        when(participantRepository.findByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "alice")).thenReturn(Optional.of(admin));
        when(participantRepository.countByConversationIdAndRoleAndDeletedAtIsNull("conv1", ConversationParticipant.Role.ADMIN)).thenReturn(2L);
        // Only reached via broadcastGroupUpdate — the promotion branch is skipped, so this list is
        // never consulted for a successor, only to notify remaining members of the departure.
        when(participantRepository.findByConversationIdAndDeletedAtIsNull("conv1")).thenReturn(List.of(otherAdmin));
        stubToDto();

        service().leaveGroup("alice", "conv1");

        assertThat(admin.getDeletedAt()).isNotNull();
        assertThat(otherAdmin.getRole()).isEqualTo(ConversationParticipant.Role.ADMIN); // unchanged — never touched by promotion logic
    }

    @Test
    void addingMembersRequiresEachOneToBeAnAcceptedConnectionOfTheAdmin() {
        // Regression test: addMembers used to skip the connection check that createGroup already
        // enforced, letting a group admin add an arbitrary stranger's user id into an existing
        // group with no relationship check at all.
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(group()));
        when(participantRepository.findByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "alice"))
                .thenReturn(Optional.of(participant("alice", ConversationParticipant.Role.ADMIN, Instant.now())));
        when(connectionRepository.existsAcceptedBetween("alice", "mallory")).thenReturn(false);

        assertThatThrownBy(() -> service().addMembers("alice", "conv1", List.of("mallory")))
                .isInstanceOf(ForbiddenException.class);

        verify(participantRepository, never()).save(any());
    }

    @Test
    void theLastAdminCannotDemoteThemselvesToMember() {
        when(conversationRepository.findById("conv1")).thenReturn(Optional.of(group()));
        when(participantRepository.findByConversationIdAndUserIdAndDeletedAtIsNull("conv1", "alice"))
                .thenReturn(Optional.of(participant("alice", ConversationParticipant.Role.ADMIN, Instant.now())));
        when(participantRepository.countByConversationIdAndRoleAndDeletedAtIsNull("conv1", ConversationParticipant.Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service().updateRole("alice", "conv1", "alice", "MEMBER"))
                .isInstanceOf(BadRequestException.class);
    }
}
