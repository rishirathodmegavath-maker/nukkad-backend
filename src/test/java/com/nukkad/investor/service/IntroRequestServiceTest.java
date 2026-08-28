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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private final InvestorMapper investorMapper = new InvestorMapper();

    private IntroRequestService service() {
        return new IntroRequestService(introRequestRepository, investorProfileRepository, startupRepository,
                ideaRepository, userRepository, userService, investorMapper, notificationService, auditService);
    }

    private IntroRequest request(String id, String requesterId, String recipientId, IntroRequestStatus status) {
        return IntroRequest.builder().id(id).requesterId(requesterId).recipientId(recipientId)
                .direction(IntroDirection.FOUNDER_TO_INVESTOR).message("Let's talk").status(status).build();
    }

    private CreateIntroRequestRequest founderToInvestor(String recipientId) {
        return new CreateIntroRequestRequest(recipientId, "FOUNDER_TO_INVESTOR", null, null, "Why this investor is a fit");
    }

    // ---- creation validation ----

    @Test
    void selfRequestIsRejected() {
        assertThatThrownBy(() -> service().create("u1", founderToInvestor("u1"))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void requestingIntroductionToNonexistentUserIsRejected() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().create("u1", founderToInvestor("ghost"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void founderToInvestorRequiresRecipientToHaveAnInvestorProfile() {
        when(userRepository.findById("u2")).thenReturn(Optional.of(User.builder().id("u2").build()));
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(false);

        assertThatThrownBy(() -> service().create("u1", founderToInvestor("u2"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void investorToFounderRequiresRequesterToHaveAnInvestorProfile() {
        when(userRepository.findById("founder1")).thenReturn(Optional.of(User.builder().id("founder1").build()));
        when(investorProfileRepository.existsByUserId("investor1")).thenReturn(false);

        CreateIntroRequestRequest req = new CreateIntroRequestRequest("founder1", "INVESTOR_TO_FOUNDER", null, null, "I'd love to learn more");

        assertThatThrownBy(() -> service().create("investor1", req)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requestingAboutANonexistentStartupIsRejected() {
        when(userRepository.findById("u2")).thenReturn(Optional.of(User.builder().id("u2").build()));
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(startupRepository.existsById("ghost-startup")).thenReturn(false);

        CreateIntroRequestRequest req = new CreateIntroRequestRequest("u2", "FOUNDER_TO_INVESTOR", "ghost-startup", null, "Context");

        assertThatThrownBy(() -> service().create("u1", req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicatePendingRequestIsRejected() {
        when(userRepository.findById("u2")).thenReturn(Optional.of(User.builder().id("u2").build()));
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(introRequestRepository.existsByRequesterIdAndRecipientIdAndStatus("u1", "u2", IntroRequestStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service().create("u1", founderToInvestor("u2"))).isInstanceOf(ConflictException.class);

        verify(introRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void validRequestPersistsAndNotifiesTheRecipient() {
        when(userRepository.findById("u2")).thenReturn(Optional.of(User.builder().id("u2").build()));
        when(investorProfileRepository.existsByUserId("u2")).thenReturn(true);
        when(introRequestRepository.existsByRequesterIdAndRecipientIdAndStatus("u1", "u2", IntroRequestStatus.PENDING)).thenReturn(false);
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

        IntroRequestDto dto = service().accept("u2", "r1");

        assertThat(dto.status()).isEqualTo("Accepted");
        verify(notificationService).notify(eq("u1"), eq(NotificationType.investor), any(), any(), any(), eq("u2"));
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
