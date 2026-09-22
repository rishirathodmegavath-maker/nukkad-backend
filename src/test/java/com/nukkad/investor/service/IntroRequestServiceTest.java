package com.nukkad.investor.service;

import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.investor.dto.CreateIntroRequestRequest;
import com.nukkad.investor.dto.IntroRequestDto;
import com.nukkad.investor.entity.IntroDirection;
import com.nukkad.investor.entity.IntroRequest;
import com.nukkad.investor.entity.IntroRequestStatus;
import com.nukkad.investor.mapper.InvestorMapper;
import com.nukkad.investor.repository.IntroRequestRepository;
import com.nukkad.investor.repository.InvestorProfileRepository;
import com.nukkad.messaging.dto.ConversationDto;
import com.nukkad.messaging.repository.ConversationRepository;
import com.nukkad.messaging.service.ConversationService;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers IntroRequest creation validation, duplicate prevention, accept/reject/withdraw authorization,
 *  and terminal-state protection — the core rules Phase 4 calls out explicitly. */
@ExtendWith(MockitoExtension.class)
class IntroRequestServiceTest {

    @Mock private IntroRequestRepository introRequestRepository;
    @Mock private InvestorProfileRepository investorProfileRepository;
    @Mock private StartupRepository startupRepository;
    @Mock private IdeaRepository ideaRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private ConversationService conversationService;
    @Mock private ConversationRepository conversationRepository;
    @Mock private com.nukkad.startup.repository.StartupTeamMemberRepository teamMemberRepository;
    private final InvestorMapper investorMapper = new InvestorMapper();

    private IntroRequestService service() {
        return new IntroRequestService(introRequestRepository, investorProfileRepository, startupRepository,
                ideaRepository, userRepository, userService, investorMapper, notificationService, auditService,
                conversationService, conversationRepository,
                new com.nukkad.startup.service.StartupAccessPolicy(startupRepository, teamMemberRepository));
    }

    private IntroRequest request(String id, String requesterId, String recipientId, IntroRequestStatus status) {
        return IntroRequest.builder().id(id).requesterId(requesterId).recipientId(recipientId)
                .direction(IntroDirection.FOUNDER_TO_INVESTOR).message("Let's talk").status(status).build();
    }

    private CreateIntroRequestRequest founderToInvestor(String recipientId) {
        return new CreateIntroRequestRequest(recipientId, "FOUNDER_TO_INVESTOR", null, null, "Why this investor is a fit");
    }

    /** The users exist: the row lock is taken on the investor, then the recipient is looked up. */
    private void usersExist(String... ids) {
        for (String id : ids) {
            User user = User.builder().id(id).build();
            lenient().when(userRepository.findByIdForUpdate(id)).thenReturn(Optional.of(user));
            lenient().when(userRepository.findById(id)).thenReturn(Optional.of(user));
        }
    }

    private com.nukkad.startup.entity.Startup liveStartup(String id) {
        return com.nukkad.startup.entity.Startup.builder().id(id).name("Ledgerly")
                .moderationStatus(com.nukkad.common.moderation.ModerationStatus.APPROVED).build();
    }

    private void userManages(String startupId, String userId) {
        when(teamMemberRepository.findByStartupIdAndUserId(startupId, userId)).thenReturn(Optional.of(
                com.nukkad.startup.entity.StartupTeamMember.builder().startupId(startupId).userId(userId)
                        .teamRole(com.nukkad.startup.entity.StartupTeamMember.TeamRole.FOUNDER)
                        .status(com.nukkad.startup.entity.StartupTeamMember.Status.ACTIVE).build()));
    }

    private IntroRequest requestAbout(String requesterId, String recipientId, IntroDirection direction, String startupId, IntroRequestStatus status) {
        return IntroRequest.builder().id("existing").requesterId(requesterId).recipientId(recipientId)
                .direction(direction).startupId(startupId).message("hi").status(status).build();
    }

    private CreateIntroRequestRequest aboutStartup(String recipientId, String startupId) {
        return new CreateIntroRequestRequest(recipientId, "FOUNDER_TO_INVESTOR", startupId, null, "Why this investor is a fit");
    }

    // ---- creation validation ----

    @Test
    void selfRequestIsRejected() {
        assertThatThrownBy(() -> service().create("u1", founderToInvestor("u1"))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void requestingIntroductionToNonexistentUserIsRejected() {
        when(userRepository.findByIdForUpdate("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().create("u1", founderToInvestor("ghost"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void founderToInvestorRequiresRecipientToHaveAnInvestorProfile() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(false);

        assertThatThrownBy(() -> service().create("u1", founderToInvestor("u2"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void investorToFounderRequiresRequesterToHaveAnInvestorProfile() {
        usersExist("investor1", "founder1");
        when(investorProfileRepository.existsByUserId("investor1")).thenReturn(false);

        CreateIntroRequestRequest req = new CreateIntroRequestRequest("founder1", "INVESTOR_TO_FOUNDER", null, null, "I'd love to learn more");

        assertThatThrownBy(() -> service().create("investor1", req)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requestingAboutANonexistentStartupIsRejected() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(startupRepository.findById("ghost-startup")).thenReturn(Optional.empty());

        CreateIntroRequestRequest req = new CreateIntroRequestRequest("u2", "FOUNDER_TO_INVESTOR", "ghost-startup", null, "Context");

        assertThatThrownBy(() -> service().create("u1", req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicatePendingRequestIsRejected() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(introRequestRepository.findActiveBetween("u1", "u2")).thenReturn(List.of(
                requestAbout("u1", "u2", IntroDirection.FOUNDER_TO_INVESTOR, null, IntroRequestStatus.PENDING)));

        assertThatThrownBy(() -> service().create("u1", founderToInvestor("u2")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("pending");

        verify(introRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void aSecondRequestIsRefusedOnceTheFirstWasAccepted() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(introRequestRepository.findActiveBetween("u1", "u2")).thenReturn(List.of(
                requestAbout("u1", "u2", IntroDirection.FOUNDER_TO_INVESTOR, null, IntroRequestStatus.ACCEPTED)));

        assertThatThrownBy(() -> service().create("u1", founderToInvestor("u2")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already been accepted");

        verify(introRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void aRequestTheInvestorStartedCountsAgainstTheFounderAskingToo() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        // u2 is the investor and reached out to founder u1 first; u1 now asks u2 about nothing in particular.
        when(introRequestRepository.findActiveBetween("u1", "u2")).thenReturn(List.of(
                requestAbout("u2", "u1", IntroDirection.INVESTOR_TO_FOUNDER, null, IntroRequestStatus.PENDING)));

        assertThatThrownBy(() -> service().create("u1", founderToInvestor("u2"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void theSameStartupAndInvestorCannotHaveTwoLiveRequests() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(liveStartup("s1")));
        userManages("s1", "u1");
        when(introRequestRepository.findActiveBetween("u1", "u2")).thenReturn(List.of(
                requestAbout("u1", "u2", IntroDirection.FOUNDER_TO_INVESTOR, "s1", IntroRequestStatus.PENDING)));

        assertThatThrownBy(() -> service().create("u1", aboutStartup("u2", "s1")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("for this startup");
    }

    @Test
    void aDifferentStartupWithTheSameInvestorIsADifferentCombination() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(startupRepository.findById("s2")).thenReturn(Optional.of(liveStartup("s2")));
        userManages("s2", "u1");
        when(introRequestRepository.findActiveBetween("u1", "u2")).thenReturn(List.of(
                requestAbout("u1", "u2", IntroDirection.FOUNDER_TO_INVESTOR, "s1", IntroRequestStatus.ACCEPTED)));
        when(introRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        IntroRequestDto dto = service().create("u1", aboutStartup("u2", "s2"));

        assertThat(dto.status()).isEqualTo("Pending");
    }

    @Test
    void aRequestWithNoStartupIsNotBlockedByOneAboutAStartup() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(introRequestRepository.findActiveBetween("u1", "u2")).thenReturn(List.of(
                requestAbout("u1", "u2", IntroDirection.FOUNDER_TO_INVESTOR, "s1", IntroRequestStatus.PENDING)));
        when(introRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service().create("u1", founderToInvestor("u2")).status()).isEqualTo("Pending");
    }

    @Test
    void theInvestorRowIsLockedBeforeTheDuplicateCheckSoSimultaneousRequestsCannotBothPass() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(introRequestRepository.findActiveBetween("u1", "u2")).thenReturn(List.of());
        when(introRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service().create("u1", founderToInvestor("u2"));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userRepository, introRequestRepository);
        order.verify(userRepository).findByIdForUpdate("u2");
        order.verify(introRequestRepository).findActiveBetween("u1", "u2");
    }

    @Test
    void aFounderCannotRequestAnIntroductionForAStartupTheyDoNotManage() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(liveStartup("s1")));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "u1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create("u1", aboutStartup("u2", "s1"))).isInstanceOf(ForbiddenException.class);

        verify(introRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void anInvestorCannotCiteAStartupTheFounderTheyContactDoesNotManage() {
        usersExist("investor1", "founder1");
        when(investorProfileRepository.existsByUserId("investor1")).thenReturn(true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(liveStartup("s1")));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "founder1")).thenReturn(Optional.empty());

        CreateIntroRequestRequest req = new CreateIntroRequestRequest("founder1", "INVESTOR_TO_FOUNDER", "s1", null, "Interested");

        assertThatThrownBy(() -> service().create("investor1", req)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aRemovedStartupCannotBeTheSubjectOfARequest() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        com.nukkad.startup.entity.Startup removed = liveStartup("s1");
        removed.setRemovedByAdmin(true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(removed));

        assertThatThrownBy(() -> service().create("u1", aboutStartup("u2", "s1"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void validRequestPersistsAndNotifiesTheRecipient() {
        usersExist("u2");
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(introRequestRepository.findActiveBetween("u1", "u2")).thenReturn(List.of());
        when(introRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        IntroRequestDto dto = service().create("u1", founderToInvestor("u2"));

        assertThat(dto.status()).isEqualTo("Pending");
        assertThat(dto.requesterId()).isEqualTo("u1");
        verify(notificationService).notify(eq("u2"), eq(NotificationType.investor), any(), any(), any(), eq("u1"));
    }

    // ---- accept / reject authorization + terminal-state protection ----

    @Test
    void onlyRecipientCanAccept() {
        IntroRequest pending = request("r1", "u1", "u2", IntroRequestStatus.PENDING);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service().accept("u1", "r1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void recipientAcceptingNotifiesTheRequester() {
        IntroRequest pending = request("r1", "u1", "u2", IntroRequestStatus.PENDING);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(pending));
        when(introRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationService.getOrCreate("u2", "u1")).thenReturn(fakeConversation("conv1"));

        IntroRequestDto dto = service().accept("u2", "r1");

        assertThat(dto.status()).isEqualTo("Accepted");
        verify(notificationService).notify(eq("u1"), eq(NotificationType.investor), any(), any(), any(), eq("u2"));
    }

    @Test
    void acceptingOpensTheConversationAtomicallyOnTheServerSide() {
        IntroRequest pending = request("r1", "u1", "u2", IntroRequestStatus.PENDING);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(pending));
        when(introRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationService.getOrCreate("u2", "u1")).thenReturn(fakeConversation("conv1"));

        IntroRequestDto dto = service().accept("u2", "r1");

        verify(conversationService).getOrCreate("u2", "u1");
        assertThat(dto.conversationId()).isEqualTo("conv1");
    }

    private ConversationDto fakeConversation(String id) {
        return new ConversationDto(id, "DIRECT", "other-user", null, null, 0L, java.time.Instant.now(), false, null, false);
    }

    @Test
    void onlyRecipientCanReject() {
        IntroRequest pending = request("r1", "u1", "u2", IntroRequestStatus.PENDING);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service().reject("u1", "r1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void onlyRequesterCanWithdraw() {
        IntroRequest pending = request("r1", "u1", "u2", IntroRequestStatus.PENDING);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service().withdraw("u2", "r1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acceptingAnAlreadyAcceptedRequestIsRejected() {
        IntroRequest accepted = request("r1", "u1", "u2", IntroRequestStatus.ACCEPTED);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> service().accept("u2", "r1")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectingAnAlreadyRejectedRequestIsRejected() {
        IntroRequest rejected = request("r1", "u1", "u2", IntroRequestStatus.REJECTED);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service().reject("u2", "r1")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptingARejectedRequestIsRejected() {
        IntroRequest rejected = request("r1", "u1", "u2", IntroRequestStatus.REJECTED);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service().accept("u2", "r1")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectingAnAcceptedRequestIsRejected() {
        IntroRequest accepted = request("r1", "u1", "u2", IntroRequestStatus.ACCEPTED);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> service().reject("u2", "r1")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void withdrawingAnAcceptedRequestIsRejected() {
        IntroRequest accepted = request("r1", "u1", "u2", IntroRequestStatus.ACCEPTED);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> service().withdraw("u1", "r1")).isInstanceOf(BadRequestException.class);
    }

    // ---- viewing authorization ----

    @Test
    void unrelatedUserCannotViewAPrivateRequest() {
        IntroRequest pending = request("r1", "u1", "u2", IntroRequestStatus.PENDING);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service().get("r1", "stranger1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void participantsCanViewTheRequest() {
        IntroRequest pending = request("r1", "u1", "u2", IntroRequestStatus.PENDING);
        when(introRequestRepository.findById("r1")).thenReturn(Optional.of(pending));

        assertThat(service().get("r1", "u1").id()).isEqualTo("r1");
        assertThat(service().get("r1", "u2").id()).isEqualTo("r1");
    }

    @Test
    void nonexistentRequestReturnsNotFound() {
        when(introRequestRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().get("ghost", "u1")).isInstanceOf(ResourceNotFoundException.class);
    }
}
