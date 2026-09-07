package com.nukkad.user.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.Connection;
import com.nukkad.user.entity.ProfileVisibility;
import com.nukkad.user.entity.User;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.MutedAccountRepository;
import com.nukkad.user.repository.UserAchievementRepository;
import com.nukkad.user.repository.UserBlockRepository;
import com.nukkad.user.repository.UserCertificationRepository;
import com.nukkad.user.repository.UserEducationRepository;
import com.nukkad.user.repository.UserExperienceRepository;
import com.nukkad.user.repository.UserFollowRepository;
import com.nukkad.user.repository.UserProjectRepository;
import com.nukkad.user.repository.UserPublicationRepository;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security-regression coverage for the connection request lifecycle (send/cancel/accept/decline),
 * connection-list/search privacy gating, and pagination abuse — this service previously had zero
 * direct unit tests despite being the sole place these rules are enforced.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ConnectionRepository connectionRepository;
    @Mock private UserFollowRepository userFollowRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private MutedAccountRepository mutedAccountRepository;
    @Mock private UserExperienceRepository userExperienceRepository;
    @Mock private UserEducationRepository userEducationRepository;
    @Mock private UserAchievementRepository userAchievementRepository;
    @Mock private UserProjectRepository userProjectRepository;
    @Mock private UserCertificationRepository userCertificationRepository;
    @Mock private UserPublicationRepository userPublicationRepository;
    @Mock private UserMapper userMapper;
    @Mock private ProfileCompletenessCalculator profileCompletenessCalculator;
    @Mock private UserEndorsementService userEndorsementService;
    @Mock private UserRecommendationService userRecommendationService;
    @Mock private ProfilePrivacyService profilePrivacyService;
    @Mock private UserPrivacySettingsService userPrivacySettingsService;
    @Mock private NotificationService notificationService;
    @Mock private FileStorageService fileStorageService;

    private UserService service() {
        return new UserService(userRepository, connectionRepository, userFollowRepository, userBlockRepository,
                mutedAccountRepository, userExperienceRepository, userEducationRepository, userAchievementRepository,
                userProjectRepository, userCertificationRepository, userPublicationRepository, userMapper,
                profileCompletenessCalculator, userEndorsementService, userRecommendationService,
                profilePrivacyService, userPrivacySettingsService, notificationService, fileStorageService);
    }

    private User user(String id, String name) {
        return User.builder().id(id).name(name).email(id + "@example.com").passwordHash("hash").build();
    }

    // ---- Self-connect / self-decline / self-follow / self-block / self-mute prevention ----

    @Test
    void cannotConnectToSelf() {
        assertThatThrownBy(() -> service().toggleConnect("alice", "alice"))
                .isInstanceOf(BadRequestException.class);
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void cannotDeclineOwnRequest() {
        assertThatThrownBy(() -> service().declineConnection("alice", "alice"))
                .isInstanceOf(BadRequestException.class);
    }

    // ---- Sending a request: duplicate/self-race safety ----

    @Test
    void sendingAFirstRequestCreatesExactlyOnePendingRow() {
        when(userRepository.findById("alice")).thenReturn(Optional.of(user("alice", "Alice")));
        when(userRepository.findById("bob")).thenReturn(Optional.of(user("bob", "Bob")));
        when(connectionRepository.findByUserAIdAndUserBId("alice", "bob")).thenReturn(Optional.empty());
        when(userBlockRepository.existsBetween("alice", "bob")).thenReturn(false);
        when(connectionRepository.findAcceptedConnections(anyString())).thenReturn(List.of());
        when(userPrivacySettingsService.canConnect(eq("bob"), any(Boolean.class))).thenReturn(true);

        var result = service().toggleConnect("alice", "bob");

        assertThat(result.status()).isEqualTo("PENDING_OUTGOING");
        ArgumentCaptor<Connection> saved = ArgumentCaptor.forClass(Connection.class);
        verify(connectionRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(Connection.Status.PENDING);
        assertThat(saved.getValue().getRequestedBy()).isEqualTo("alice");
    }

    @Test
    void aBlockedUserCannotSendAConnectionRequest() {
        when(userRepository.findById("alice")).thenReturn(Optional.of(user("alice", "Alice")));
        when(userRepository.findById("bob")).thenReturn(Optional.of(user("bob", "Bob")));
        when(connectionRepository.findByUserAIdAndUserBId("alice", "bob")).thenReturn(Optional.empty());
        when(userBlockRepository.existsBetween("alice", "bob")).thenReturn(true);

        assertThatThrownBy(() -> service().toggleConnect("alice", "bob")).isInstanceOf(ForbiddenException.class);
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void sendingASecondRequestWhileOneIsAlreadyPendingCancelsRatherThanDuplicating() {
        when(userRepository.findById("alice")).thenReturn(Optional.of(user("alice", "Alice")));
        when(userRepository.findById("bob")).thenReturn(Optional.of(user("bob", "Bob")));
        Connection existing = Connection.builder().id("c1").userAId("alice").userBId("bob")
                .requestedBy("alice").status(Connection.Status.PENDING).build();
        when(connectionRepository.findByUserAIdAndUserBId("alice", "bob")).thenReturn(Optional.of(existing));

        var result = service().toggleConnect("alice", "bob");

        assertThat(result.status()).isEqualTo("NONE");
        verify(connectionRepository).delete(existing);
        verify(connectionRepository, never()).save(any());
    }

    // ---- Accept: only the recipient's toggleConnect call accepts; the requester's cancels ----

    @Test
    void onlyTheRecipientAcceptingTurnsAPendingRequestIntoAConnection() {
        when(userRepository.findById("alice")).thenReturn(Optional.of(user("alice", "Alice")));
        when(userRepository.findById("bob")).thenReturn(Optional.of(user("bob", "Bob")));
        Connection existing = Connection.builder().id("c1").userAId("alice").userBId("bob")
                .requestedBy("alice").status(Connection.Status.PENDING).build();
        when(connectionRepository.findByUserAIdAndUserBId("alice", "bob")).thenReturn(Optional.of(existing));

        // bob is the recipient of alice's request: bob calling toggleConnect accepts it.
        var result = service().toggleConnect("bob", "alice");

        assertThat(result.status()).isEqualTo("CONNECTED");
        assertThat(existing.getStatus()).isEqualTo(Connection.Status.ACCEPTED);
        verify(connectionRepository, never()).delete(any());
    }

    @Test
    void anAlreadyAcceptedConnectionCannotBeDuplicatedAndTogglingItRemovesIt() {
        when(userRepository.findById("alice")).thenReturn(Optional.of(user("alice", "Alice")));
        when(userRepository.findById("bob")).thenReturn(Optional.of(user("bob", "Bob")));
        Connection existing = Connection.builder().id("c1").userAId("alice").userBId("bob")
                .requestedBy("alice").status(Connection.Status.ACCEPTED).build();
        when(connectionRepository.findByUserAIdAndUserBId("alice", "bob")).thenReturn(Optional.of(existing));

        var result = service().toggleConnect("alice", "bob");

        assertThat(result.status()).isEqualTo("NONE");
        verify(connectionRepository).delete(existing);
        verify(connectionRepository, never()).save(any());
    }

    // ---- Decline: a third party cannot manipulate someone else's request ----

    @Test
    void decliningRequiresAnActualPendingRequestAddressedToTheViewer() {
        // mallory tries to decline a request that doesn't exist between her and bob.
        when(userRepository.findById("bob")).thenReturn(Optional.of(user("bob", "Bob")));
        when(connectionRepository.findByUserAIdAndUserBId("bob", "mallory")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().declineConnection("mallory", "bob"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(connectionRepository, never()).delete(any());
    }

    @Test
    void aRequesterCannotDeclineTheirOwnOutgoingRequestThroughTheDeclineEndpoint() {
        // alice sent bob a request. alice cannot call decline("alice", "bob") to wipe her own
        // outgoing request out from under bob (that must go through cancel, i.e. toggleConnect).
        Connection existing = Connection.builder().id("c1").userAId("alice").userBId("bob")
                .requestedBy("alice").status(Connection.Status.PENDING).build();
        when(userRepository.findById("bob")).thenReturn(Optional.of(user("bob", "Bob")));
        when(connectionRepository.findByUserAIdAndUserBId("alice", "bob")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().declineConnection("alice", "bob"))
                .isInstanceOf(BadRequestException.class);
        verify(connectionRepository, never()).delete(any());
    }

    @Test
    void theActualRecipientCanDeclineAPendingRequest() {
        Connection existing = Connection.builder().id("c1").userAId("alice").userBId("bob")
                .requestedBy("alice").status(Connection.Status.PENDING).build();
        when(userRepository.findById("alice")).thenReturn(Optional.of(user("alice", "Alice")));
        when(connectionRepository.findByUserAIdAndUserBId("alice", "bob")).thenReturn(Optional.of(existing));

        service().declineConnection("bob", "alice");

        verify(connectionRepository).delete(existing);
    }

    // ---- listUserConnections: restricted profiles cannot be enumerated by strangers ----

    @Test
    void aStrangerCannotEnumerateAConnectionsListRestrictedProfilesConnections() {
        when(userRepository.findById("target")).thenReturn(Optional.of(user("target", "Target")));
        when(userPrivacySettingsService.isProfileRestricted("target", "stranger", false)).thenReturn(true);

        List<UserDto> result = service().listUserConnections("stranger", "target");

        assertThat(result).isEmpty();
        verify(connectionRepository, never()).findAcceptedConnections(anyString());
    }

    @Test
    void aConnectionPartnersOwnRestrictedProfileIsHiddenEvenWhileListingSomeoneElsesConnections() {
        // viewer is connected to "target" and so may see target's connections list. One of
        // target's connections ("restrictedPartner") has set their own profile to CONNECTIONS-only
        // and has no relationship with viewer — their extended profile fields must not leak here.
        when(userRepository.findById("target")).thenReturn(Optional.of(user("target", "Target")));
        // viewer is actually connected to target, so the top-level "is this whole list gated" check
        // (isConnected computed from this edge) must see CONNECTED, matching the isProfileRestricted stub below.
        Connection viewerTargetEdge = Connection.builder().id("vt").userAId("target").userBId("viewer")
                .requestedBy("target").status(Connection.Status.ACCEPTED).build();
        when(connectionRepository.findByUserAIdAndUserBId("target", "viewer")).thenReturn(Optional.of(viewerTargetEdge));
        when(userPrivacySettingsService.isProfileRestricted("target", "viewer", true)).thenReturn(false);
        Connection edge = Connection.builder().id("c1").userAId("restrictedPartner").userBId("target")
                .requestedBy("target").status(Connection.Status.ACCEPTED).build();
        when(connectionRepository.findAcceptedConnections("target")).thenReturn(List.of(edge));
        User restrictedPartner = user("restrictedPartner", "Restricted");
        when(userRepository.findAllById(List.of("restrictedPartner"))).thenReturn(List.of(restrictedPartner));
        when(connectionRepository.findAllInvolvingViewer(eq("viewer"), any())).thenReturn(List.of());
        when(userPrivacySettingsService.bulkProfileVisibility(List.of("restrictedPartner")))
                .thenReturn(java.util.Map.of("restrictedPartner", ProfileVisibility.CONNECTIONS));

        service().listUserConnections("viewer", "target");

        verify(userMapper).toRestrictedDto(eq(restrictedPartner), eq("NONE"), eq(null));
        verify(userMapper, never()).toDto(eq(restrictedPartner), anyString(), any());
    }

    // ---- listUsers: pagination cannot be abused ----

    @Test
    void searchPageSizeIsClampedRegardlessOfWhatTheCallerRequests() {
        User viewer = user("viewer", "Viewer");
        when(userBlockRepository.findBlockedEitherWayIds("viewer")).thenReturn(java.util.Set.of());
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().listUsers("viewer", null, null, null, null, null, null, null, null, 0, 100_000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isLessThanOrEqualTo(50);
    }

    @Test
    void searchDoesNotThrowOnANegativePageNumber() {
        when(userBlockRepository.findBlockedEitherWayIds("viewer")).thenReturn(java.util.Set.of());
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<UserDto> result = service().listUsers("viewer", null, null, null, null, null, null, null, null, -5, 20);

        assertThat(result).isNotNull();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    }

    // ---- listUsers: a chapterId filter is the chapter Members tab, not the People directory ----

    @SuppressWarnings("unchecked")
    @Test
    void chapterMembersListDoesNotExcludeTheViewerFromTheirOwnChapter() {
        when(userBlockRepository.findBlockedEitherWayIds("president")).thenReturn(java.util.Set.of());
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().listUsers("president", null, null, null, null, null, null, null, "chapter-1", 0, 20);

        ArgumentCaptor<Specification<User>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(userRepository).findAll(specCaptor.capture(), any(Pageable.class));

        jakarta.persistence.criteria.Root<User> root = mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.CriteriaQuery<?> query = mock(jakarta.persistence.criteria.CriteriaQuery.class);
        jakarta.persistence.criteria.CriteriaBuilder cb = mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        jakarta.persistence.criteria.Path<Object> path = mock(jakarta.persistence.criteria.Path.class);
        org.mockito.Mockito.lenient().when(root.<Object>get(anyString())).thenReturn(path);
        org.mockito.Mockito.lenient().when(cb.equal(any(), any())).thenReturn(mock(jakarta.persistence.criteria.Predicate.class));
        org.mockito.Mockito.lenient().when(cb.and(any(), any())).thenReturn(mock(jakarta.persistence.criteria.Predicate.class));
        org.mockito.Mockito.lenient().when(cb.conjunction()).thenReturn(mock(jakarta.persistence.criteria.Predicate.class));

        specCaptor.getValue().toPredicate(root, query, cb);

        // The viewer (chapter president) must not be filtered out — otherwise a chapter whose only
        // member is its own president renders an incorrect "no members joined" empty state.
        verify(cb, never()).notEqual(any(), eq("president"));
        verify(cb).equal(path, "chapter-1");
    }

    // ---- Incoming/sent request lists: scoped to the authenticated viewer only ----

    @Test
    void incomingRequestsAreFetchedOnlyForTheAuthenticatedViewer() {
        when(connectionRepository.findPendingIncoming("alice")).thenReturn(List.of());

        service().listIncomingRequests("alice");

        verify(connectionRepository).findPendingIncoming("alice");
        verify(connectionRepository, never()).findPendingOutgoing(anyString());
    }

    @Test
    void sentRequestsAreFetchedOnlyForTheAuthenticatedViewer() {
        when(connectionRepository.findPendingOutgoing("alice")).thenReturn(List.of());

        service().listSentRequests("alice");

        verify(connectionRepository).findPendingOutgoing("alice");
        verify(connectionRepository, never()).findPendingIncoming(anyString());
    }

    @Test
    void incomingRequestPartnersAreResolvedAndTheirOwnPrivacySettingIsRespected() {
        Connection incoming = Connection.builder().id("c1").userAId("alice").userBId("bob")
                .requestedBy("bob").status(Connection.Status.PENDING).build();
        when(connectionRepository.findPendingIncoming("alice")).thenReturn(List.of(incoming));
        User bob = user("bob", "Bob");
        when(userRepository.findAllById(List.of("bob"))).thenReturn(List.of(bob));
        when(connectionRepository.findAllInvolvingViewer(eq("alice"), any())).thenReturn(List.of(incoming));
        when(userPrivacySettingsService.bulkProfileVisibility(List.of("bob")))
                .thenReturn(java.util.Map.of("bob", ProfileVisibility.EVERYONE));

        service().listIncomingRequests("alice");

        verify(userMapper).toDto(eq(bob), eq("PENDING_INCOMING"), eq(null));
    }
}
