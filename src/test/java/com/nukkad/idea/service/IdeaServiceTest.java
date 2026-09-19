package com.nukkad.idea.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.idea.dto.ConvertToStartupRequest;
import com.nukkad.idea.dto.ExpressInterestRequest;
import com.nukkad.idea.dto.IdeaDto;
import com.nukkad.idea.dto.IdeaInterestDto;
import com.nukkad.idea.dto.PostIdeaRequest;
import com.nukkad.idea.dto.UpdateIdeaRequest;
import com.nukkad.idea.entity.Idea;
import com.nukkad.idea.entity.IdeaInterest;
import com.nukkad.idea.entity.IdeaInterestStatus;
import com.nukkad.idea.entity.IdeaStage;
import com.nukkad.idea.mapper.IdeaMapper;
import com.nukkad.idea.repository.IdeaInterestRepository;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.mapper.StartupMapper;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.User;
import com.nukkad.user.entity.UserExperience;
import com.nukkad.user.entity.UserProject;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.UserExperienceRepository;
import com.nukkad.user.repository.UserProjectRepository;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Idea lifecycle's state machine (interest -> shortlist/reject -> team -> convert-to-startup),
 * authorization (only the creator can manage/convert/edit; interest details are private to their owner
 * and the creator), and the Idea -> Startup conversion workflow's persisted side effects.
 */
@ExtendWith(MockitoExtension.class)
class IdeaServiceTest {

    @Mock private IdeaRepository ideaRepository;
    @Mock private IdeaInterestRepository ideaInterestRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserExperienceRepository userExperienceRepository;
    @Mock private UserProjectRepository userProjectRepository;
    @Mock private StartupRepository startupRepository;
    @Mock private StartupTeamMemberRepository startupTeamMemberRepository;
    @Mock private UserMapper userMapper;
    @Mock private UserService userService;
    @Mock private StartupMapper startupMapper;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;

    private final IdeaMapper ideaMapper = new IdeaMapper();

    private IdeaService service() {
        return new IdeaService(ideaRepository, ideaInterestRepository, userRepository, userExperienceRepository,
                userProjectRepository, startupRepository, startupTeamMemberRepository, ideaMapper, userMapper,
                userService, startupMapper, notificationService, auditService);
    }

    private Idea idea(String creatorId) {
        return Idea.builder().id("idea1").title("AI Tutor").problem("Students struggle to get 1:1 help")
                .solution("An adaptive AI tutor").stage(IdeaStage.CONCEPT).creatorId(creatorId)
                .tags(new HashSet<>()).helpNeeded(new HashSet<>())
                .teamMemberIds(new HashSet<>(Set.of(creatorId)))
                .moderationStatus(com.nukkad.common.moderation.ModerationStatus.APPROVED).build();
    }

    private IdeaInterest interest(String ideaId, String userId, IdeaInterestStatus status) {
        return IdeaInterest.builder().id("int1").ideaId(ideaId).userId(userId).status(status)
                .contributionAreas(new HashSet<>()).relevantSkills(new ArrayList<>())
                .experienceIds(new ArrayList<>()).projectIds(new ArrayList<>()).build();
    }

    private User user(String id, String name) {
        return User.builder().id(id).name(name).skills(new HashSet<>()).build();
    }

    private UserDto stubUserDto(String id) {
        return new UserDto(id, id, null, null, null, null, null, null, null, 0,
                Set.of(), Set.of(), Set.of(), Map.of(), null, null, null, null, 0, false, null,
                null, null, null, null, null, null, null, null, null, null, null, Set.of(), false, true);
    }

    // ---- Posting an idea ----

    @Test
    void postingAnIdeaAddsCreatorToTheTeamAndLogsAudit() {
        when(userRepository.findById("creator1")).thenReturn(Optional.of(user("creator1", "Priya")));
        when(ideaRepository.saveAndFlush(any(Idea.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(0L);

        PostIdeaRequest request = new PostIdeaRequest("AI Tutor", "Students struggle", "Adaptive tutor",
                "High schoolers", "Concept", "EdTech", Set.of("ai"), Set.of("Technology"));

        IdeaDto dto = service().postIdea("creator1", request);

        assertThat(dto.creatorId()).isEqualTo("creator1");
        assertThat(dto.teamMemberIds()).containsExactly("creator1");
        verify(auditService).log(eq("creator1"), eq(AuditAction.CREATE_IDEA), eq("Idea"), any(), isNull());
    }

    // ---- Admin moderation ----

    @Test
    void publicGetterThrowsNotFoundForARemovedIdea() {
        Idea idea = idea("creator1");
        idea.setRemovedByAdmin(true);
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().getIdea("idea1", "someoneElse")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminGetterStillReturnsARemovedIdea() {
        Idea idea = idea("creator1");
        idea.setRemovedByAdmin(true);
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(0L);

        IdeaDto dto = service().getIdeaForAdmin("idea1");

        assertThat(dto.removedByAdmin()).isTrue();
    }

    @Test
    void adminCanRemoveAndRestoreAnIdeaWithAudit() {
        Idea idea = idea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(ideaRepository.saveAndFlush(any(Idea.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(0L);

        IdeaDto removed = service().setRemovedByAdmin("admin1", "idea1", true, "Spam", "1.2.3.4");
        assertThat(removed.removedByAdmin()).isTrue();
        assertThat(removed.removalReason()).isEqualTo("Spam");
        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_CONTENT_REMOVED), eq("Idea"), eq("idea1"),
                eq("1.2.3.4"), any());

        IdeaDto restored = service().setRemovedByAdmin("admin1", "idea1", false, null, "1.2.3.4");
        assertThat(restored.removedByAdmin()).isFalse();
        assertThat(restored.removalReason()).isNull();
        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_CONTENT_RESTORED), eq("Idea"), eq("idea1"),
                eq("1.2.3.4"), any());
    }

    // ---- Pre-publish moderation ----

    private Idea pendingIdea(String creatorId) {
        Idea idea = idea(creatorId);
        idea.setModerationStatus(com.nukkad.common.moderation.ModerationStatus.PENDING);
        return idea;
    }

    @Test
    void publicGetterHidesAPendingIdeaFromEveryoneButItsCreator() {
        Idea idea = pendingIdea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().getIdea("idea1", "someoneElse")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void publicGetterStillShowsAPendingIdeaToItsOwnCreator() {
        Idea idea = pendingIdea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(0L);

        IdeaDto dto = service().getIdea("idea1", "creator1");

        assertThat(dto.moderationStatus()).isEqualTo("PENDING");
    }

    @Test
    void approvingAPendingIdeaLogsAuditAndNotifiesTheCreator() {
        Idea idea = pendingIdea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(ideaRepository.saveAndFlush(any(Idea.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(0L);

        IdeaDto dto = service().reviewModeration("admin1", "idea1", true, null, "1.2.3.4");

        assertThat(dto.moderationStatus()).isEqualTo("APPROVED");
        assertThat(dto.rejectionReason()).isNull();
        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_CONTENT_APPROVED), eq("Idea"), eq("idea1"),
                eq("1.2.3.4"), any());
        verify(notificationService).notify(eq("creator1"), any(), anyString(), anyString(), eq("idea1"), eq("admin1"));
    }

    @Test
    void rejectingAPendingIdeaRequiresAReasonAndRecordsIt() {
        Idea idea = pendingIdea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().reviewModeration("admin1", "idea1", false, null, "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);

        when(ideaRepository.saveAndFlush(any(Idea.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(0L);

        IdeaDto dto = service().reviewModeration("admin1", "idea1", false, "Low quality", "1.2.3.4");
        assertThat(dto.moderationStatus()).isEqualTo("REJECTED");
        assertThat(dto.rejectionReason()).isEqualTo("Low quality");
        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_CONTENT_REJECTED), eq("Idea"), eq("idea1"),
                eq("1.2.3.4"), any());
    }

    @Test
    void anAlreadyReviewedIdeaCannotBeReviewedAgain() {
        Idea idea = idea("creator1"); // fixture defaults to APPROVED
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().reviewModeration("admin1", "idea1", true, null, "1.2.3.4"))
                .isInstanceOf(ConflictException.class);
    }

    // ---- Update / delete authorization ----

    @Test
    void creatorCanPartiallyUpdateAnIdea() {
        Idea idea = idea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(ideaRepository.saveAndFlush(any(Idea.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(0L);

        UpdateIdeaRequest request = new UpdateIdeaRequest("New title", null, null, null, null, null, null, null);
        IdeaDto dto = service().updateIdea("creator1", "idea1", request);

        assertThat(dto.title()).isEqualTo("New title");
        assertThat(dto.problem()).isEqualTo(idea.getProblem());
    }

    @Test
    void nonCreatorCannotUpdateAnIdea() {
        Idea idea = idea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        UpdateIdeaRequest request = new UpdateIdeaRequest("x", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service().updateIdea("stranger1", "idea1", request))
                .isInstanceOf(ForbiddenException.class);
        verify(ideaRepository, never()).saveAndFlush(any());
    }

    @Test
    void creatorCanDeleteAnIdeaThatHasNotConverted() {
        Idea idea = idea("creator1");
        IdeaInterest existing = interest("idea1", "applicant1", IdeaInterestStatus.PENDING);
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(ideaInterestRepository.findByIdeaId("idea1")).thenReturn(List.of(existing));

        service().deleteIdea("creator1", "idea1");

        verify(ideaInterestRepository).deleteById(existing.getId());
        verify(ideaRepository).delete(idea);
        verify(auditService).log(eq("creator1"), eq(AuditAction.DELETE_IDEA), eq("Idea"), eq("idea1"), isNull());
    }

    @Test
    void nonCreatorCannotDeleteAnIdea() {
        Idea idea = idea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().deleteIdea("stranger1", "idea1")).isInstanceOf(ForbiddenException.class);
        verify(ideaRepository, never()).delete(any(Idea.class));
    }

    @Test
    void cannotDeleteAnIdeaThatHasAlreadyConverted() {
        Idea idea = idea("creator1");
        idea.setStartupId("startup1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().deleteIdea("creator1", "idea1")).isInstanceOf(ConflictException.class);
        verify(ideaRepository, never()).delete(any(Idea.class));
    }

    // ---- Express interest ----

    @Test
    void expressingInterestNotifiesCreator() {
        Idea idea = idea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(user("applicant1", "Rahul")));
        when(ideaInterestRepository.findByIdeaIdAndUserId("idea1", "applicant1")).thenReturn(Optional.empty());
        when(ideaInterestRepository.saveAndFlush(any(IdeaInterest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getUser("applicant1", "applicant1")).thenReturn(stubUserDto("applicant1"));

        ExpressInterestRequest request = new ExpressInterestRequest(Set.of("Technology"), "I can help", null, null, null);

        IdeaInterestDto dto = service().expressInterest("applicant1", "idea1", request);

        assertThat(dto.status()).isEqualTo("Pending");
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq("creator1"), eq(NotificationType.idea_interest), eq("New interest in your idea"),
                message.capture(), eq("idea1"), eq("applicant1"));
        assertThat(message.getValue()).contains("Rahul").contains("AI Tutor");
    }

    @Test
    void creatorCannotExpressInterestInOwnIdea() {
        Idea idea = idea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        ExpressInterestRequest request = new ExpressInterestRequest(Set.of("Technology"), null, null, null, null);

        assertThatThrownBy(() -> service().expressInterest("creator1", "idea1", request))
                .isInstanceOf(BadRequestException.class);
        verify(ideaInterestRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateInterestIsRejectedWhenNotWithdrawn() {
        Idea idea = idea("creator1");
        IdeaInterest existing = interest("idea1", "applicant1", IdeaInterestStatus.PENDING);
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(user("applicant1", "Meera")));
        when(ideaInterestRepository.findByIdeaIdAndUserId("idea1", "applicant1")).thenReturn(Optional.of(existing));

        ExpressInterestRequest request = new ExpressInterestRequest(Set.of("Technology"), null, null, null, null);

        assertThatThrownBy(() -> service().expressInterest("applicant1", "idea1", request))
                .isInstanceOf(BadRequestException.class);
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reapplyingAfterWithdrawalReusesSameRowAndResetsToPending() {
        Idea idea = idea("creator1");
        IdeaInterest existing = interest("idea1", "applicant1", IdeaInterestStatus.WITHDRAWN);
        existing.setReviewedAt(Instant.now());
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(user("applicant1", "Rahul")));
        when(ideaInterestRepository.findByIdeaIdAndUserId("idea1", "applicant1")).thenReturn(Optional.of(existing));
        when(ideaInterestRepository.saveAndFlush(any(IdeaInterest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getUser("applicant1", "applicant1")).thenReturn(stubUserDto("applicant1"));

        ExpressInterestRequest request = new ExpressInterestRequest(Set.of("Design"), "Trying again", null, null, null);

        IdeaInterestDto dto = service().expressInterest("applicant1", "idea1", request);

        assertThat(dto.status()).isEqualTo("Pending");
        ArgumentCaptor<IdeaInterest> captor = ArgumentCaptor.forClass(IdeaInterest.class);
        verify(ideaInterestRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
        assertThat(captor.getValue().getReviewedAt()).isNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(IdeaInterestStatus.PENDING);
    }

    @Test
    void expressInterestFiltersOutExperienceAndProjectIdsNotOwnedByApplicant() {
        Idea idea = idea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(user("applicant1", "Rahul")));
        when(ideaInterestRepository.findByIdeaIdAndUserId("idea1", "applicant1")).thenReturn(Optional.empty());
        when(userExperienceRepository.findByUser_IdOrderBySortOrderAsc("applicant1"))
                .thenReturn(List.of(UserExperience.builder().id("exp-owned").build()));
        when(userProjectRepository.findByUser_IdOrderBySortOrderAsc("applicant1"))
                .thenReturn(List.of(UserProject.builder().id("proj-owned").build()));
        when(ideaInterestRepository.saveAndFlush(any(IdeaInterest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getUser("applicant1", "applicant1")).thenReturn(stubUserDto("applicant1"));

        ExpressInterestRequest request = new ExpressInterestRequest(Set.of("Technology"), null, null,
                List.of("exp-owned", "exp-not-owned"), List.of("proj-owned", "proj-not-owned"));

        service().expressInterest("applicant1", "idea1", request);

        ArgumentCaptor<IdeaInterest> captor = ArgumentCaptor.forClass(IdeaInterest.class);
        verify(ideaInterestRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getExperienceIds()).containsExactly("exp-owned");
        assertThat(captor.getValue().getProjectIds()).containsExactly("proj-owned");
    }

    // ---- Defect fix: an already-converted idea stops accepting new interest ----

    @Test
    void expressingInterestInAnIdeaThatHasBecomeAStartupIsRejected() {
        Idea idea = idea("creator1");
        idea.setStartupId("startup1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        ExpressInterestRequest request = new ExpressInterestRequest(Set.of("Technology"), null, null, null, null);

        assertThatThrownBy(() -> service().expressInterest("applicant1", "idea1", request))
                .isInstanceOf(BadRequestException.class);
        verify(ideaInterestRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    // ---- Withdraw interest ----

    @Test
    void withdrawingInterestNotifiesCreator() {
        Idea idea = idea("creator1");
        IdeaInterest existing = interest("idea1", "applicant1", IdeaInterestStatus.PENDING);
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(ideaInterestRepository.findByIdeaIdAndUserId("idea1", "applicant1")).thenReturn(Optional.of(existing));
        when(ideaInterestRepository.saveAndFlush(any(IdeaInterest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(user("applicant1", "Rahul")));

        service().withdrawInterest("applicant1", "idea1");

        assertThat(existing.getStatus()).isEqualTo(IdeaInterestStatus.WITHDRAWN);
        assertThat(existing.getReviewedAt()).isNotNull();
        verify(notificationService).notify(eq("creator1"), eq(NotificationType.idea_interest), eq("Interest withdrawn"),
                any(), eq("idea1"), eq("applicant1"));
    }

    @Test
    void withdrawingATerminalInterestIsRejected() {
        for (IdeaInterestStatus terminal : List.of(IdeaInterestStatus.ACCEPTED, IdeaInterestStatus.REJECTED, IdeaInterestStatus.WITHDRAWN)) {
            Idea idea = idea("creator1");
            IdeaInterest existing = interest("idea1", "applicant1", terminal);
            when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
            when(ideaInterestRepository.findByIdeaIdAndUserId("idea1", "applicant1")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service().withdrawInterest("applicant1", "idea1"))
                    .isInstanceOf(BadRequestException.class);
        }
        verify(ideaInterestRepository, never()).saveAndFlush(any());
    }

    // ---- Shortlist / reject transitions ----

    @Test
    void shortlistTransitionNotifiesApplicant() {
        Idea idea = idea("creator1");
        IdeaInterest existing = interest("idea1", "applicant1", IdeaInterestStatus.PENDING);
        when(ideaInterestRepository.findById("int1")).thenReturn(Optional.of(existing));
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(ideaInterestRepository.saveAndFlush(any(IdeaInterest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("creator1")).thenReturn(Optional.of(user("creator1", "Priya")));
        when(userService.getUser("applicant1", "creator1")).thenReturn(stubUserDto("applicant1"));

        IdeaInterestDto dto = service().shortlistInterest("creator1", "int1");

        assertThat(dto.status()).isEqualTo("Shortlisted");
        assertThat(existing.getReviewedAt()).isNotNull();
        verify(notificationService).notify(eq("applicant1"), eq(NotificationType.idea_interest), eq("Shortlisted for your idea"),
                any(), eq("idea1"), eq("creator1"));
    }

    @Test
    void rejectTransitionNotifiesApplicant() {
        Idea idea = idea("creator1");
        IdeaInterest existing = interest("idea1", "applicant1", IdeaInterestStatus.PENDING);
        when(ideaInterestRepository.findById("int1")).thenReturn(Optional.of(existing));
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(ideaInterestRepository.saveAndFlush(any(IdeaInterest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("creator1")).thenReturn(Optional.of(user("creator1", "Priya")));
        when(userService.getUser("applicant1", "creator1")).thenReturn(stubUserDto("applicant1"));

        IdeaInterestDto dto = service().rejectInterest("creator1", "int1");

        assertThat(dto.status()).isEqualTo("Rejected");
        verify(notificationService).notify(eq("applicant1"), eq(NotificationType.idea_interest), eq("Update on your interest"),
                any(), eq("idea1"), eq("creator1"));
    }

    @Test
    void nonCreatorCannotTransitionInterestStatus() {
        Idea idea = idea("creator1");
        IdeaInterest existing = interest("idea1", "applicant1", IdeaInterestStatus.PENDING);
        when(ideaInterestRepository.findById("int1")).thenReturn(Optional.of(existing));
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().shortlistInterest("stranger1", "int1")).isInstanceOf(ForbiddenException.class);
        verify(ideaInterestRepository, never()).saveAndFlush(any());
    }

    @Test
    void transitionsOnTerminalInterestsAreRejected() {
        for (IdeaInterestStatus terminal : List.of(IdeaInterestStatus.ACCEPTED, IdeaInterestStatus.REJECTED, IdeaInterestStatus.WITHDRAWN)) {
            Idea idea = idea("creator1");
            IdeaInterest existing = interest("idea1", "applicant1", terminal);
            when(ideaInterestRepository.findById("int1")).thenReturn(Optional.of(existing));
            when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

            IdeaService svc = service();
            assertThatThrownBy(() -> svc.shortlistInterest("creator1", "int1")).isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> svc.rejectInterest("creator1", "int1")).isInstanceOf(BadRequestException.class);
        }
        verify(ideaInterestRepository, never()).saveAndFlush(any());
    }

    // ---- Interest visibility: creator sees everyone's, others only see their own ----

    @Test
    void creatorSeesAllInterestsButOthersOnlySeeTheirOwn() {
        Idea idea = idea("creator1");
        IdeaInterest interestA = interest("idea1", "applicant1", IdeaInterestStatus.PENDING);
        IdeaInterest interestB = interest("idea1", "applicant2", IdeaInterestStatus.PENDING);
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(userService.getUser(anyString(), anyString())).thenReturn(stubUserDto("x"));

        when(ideaInterestRepository.findByIdeaId("idea1")).thenReturn(List.of(interestA, interestB));
        IdeaService.IdeaMembers creatorView = service().getMembers("creator1", "idea1");
        assertThat(creatorView.interests()).hasSize(2);

        when(ideaInterestRepository.findByIdeaIdAndUserId("idea1", "applicant1")).thenReturn(Optional.of(interestA));
        IdeaService.IdeaMembers applicantView = service().getMembers("applicant1", "idea1");
        assertThat(applicantView.interests()).hasSize(1);
        assertThat(applicantView.interests().get(0).id()).isEqualTo(interestA.getId());
    }

    // ---- Team management ----

    @Test
    void addingToTeamMarksMatchingInterestAcceptedAndNotifies() {
        Idea idea = idea("creator1");
        IdeaInterest existing = interest("idea1", "applicant1", IdeaInterestStatus.SHORTLISTED);
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(user("applicant1", "Rahul")));
        when(ideaRepository.saveAndFlush(any(Idea.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ideaInterestRepository.findByIdeaIdAndUserId("idea1", "applicant1")).thenReturn(Optional.of(existing));
        when(ideaInterestRepository.saveAndFlush(any(IdeaInterest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(1L);

        IdeaDto dto = service().addToTeam("creator1", "idea1", "applicant1");

        assertThat(dto.teamMemberIds()).containsExactlyInAnyOrder("creator1", "applicant1");
        assertThat(existing.getStatus()).isEqualTo(IdeaInterestStatus.ACCEPTED);
        verify(notificationService).notify(eq("applicant1"), eq(NotificationType.idea_interest), eq("You joined a team"),
                any(), eq("idea1"), eq("creator1"));
    }

    @Test
    void onlyCreatorCanAddToTeam() {
        Idea idea = idea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().addToTeam("stranger1", "idea1", "applicant1"))
                .isInstanceOf(ForbiddenException.class);
        verify(ideaRepository, never()).saveAndFlush(any());
    }

    @Test
    void cannotAddToTeamOnceIdeaHasBecomeAStartup() {
        Idea idea = idea("creator1");
        idea.setStartupId("startup1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().addToTeam("creator1", "idea1", "applicant1"))
                .isInstanceOf(BadRequestException.class);
        verify(ideaRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    void creatorCanRemoveATeamMember() {
        Idea idea = idea("creator1");
        idea.setTeamMemberIds(new HashSet<>(Set.of("creator1", "applicant1")));
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(ideaRepository.saveAndFlush(any(Idea.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(0L);

        IdeaDto dto = service().removeFromTeam("creator1", "idea1", "applicant1");

        assertThat(dto.teamMemberIds()).containsExactly("creator1");
    }

    @Test
    void memberCanRemoveThemselvesFromTheTeam() {
        Idea idea = idea("creator1");
        idea.setTeamMemberIds(new HashSet<>(Set.of("creator1", "applicant1")));
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(ideaRepository.saveAndFlush(any(Idea.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(0L);

        IdeaDto dto = service().removeFromTeam("applicant1", "idea1", "applicant1");

        assertThat(dto.teamMemberIds()).containsExactly("creator1");
    }

    @Test
    void creatorCannotBeRemovedFromTheTeam() {
        Idea idea = idea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().removeFromTeam("creator1", "idea1", "creator1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void unrelatedUserCannotRemoveSomeoneElseFromTheTeam() {
        Idea idea = idea("creator1");
        idea.setTeamMemberIds(new HashSet<>(Set.of("creator1", "applicant1")));
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().removeFromTeam("stranger1", "idea1", "applicant1"))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- Convert idea to startup ----

    @Test
    void convertingToStartupMigratesTeamAssignsFounderAndNotifiesWithStartupType() {
        Idea idea = idea("creator1");
        idea.setTeamMemberIds(new HashSet<>(Set.of("creator1", "member1")));
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(startupRepository.saveAndFlush(any(Startup.class))).thenAnswer(inv -> {
            Startup s = inv.getArgument(0);
            s.setId("startup1");
            return s;
        });
        when(ideaRepository.save(any(Idea.class))).thenAnswer(inv -> inv.getArgument(0));

        service().convertToStartup("creator1", "idea1", new ConvertToStartupRequest(null, null, null));

        assertThat(idea.getStartupId()).isEqualTo("startup1");

        ArgumentCaptor<StartupTeamMember> memberCaptor = ArgumentCaptor.forClass(StartupTeamMember.class);
        verify(startupTeamMemberRepository, times(2)).save(memberCaptor.capture());
        assertThat(memberCaptor.getAllValues()).extracting(StartupTeamMember::getUserId)
                .containsExactlyInAnyOrder("creator1", "member1");
        memberCaptor.getAllValues().forEach(m -> assertThat(m.isFounder()).isEqualTo(m.getUserId().equals("creator1")));

        // Regression: this notification's entity is a Startup, not an Idea — it must be typed
        // "startup" (routes the frontend to /startups/{id}), not "idea_interest" (which would
        // route to the wrong module, /ideas/{startupId}, since the id here is a startup id).
        verify(notificationService).notify(eq("creator1"), eq(NotificationType.startup), eq("Idea became a startup"),
                any(), eq("startup1"), eq("creator1"));
        verify(notificationService).notify(eq("member1"), eq(NotificationType.startup), eq("Idea became a startup"),
                any(), eq("startup1"), eq("creator1"));
        verify(notificationService, never()).notify(any(), eq(NotificationType.idea_interest), eq("Idea became a startup"),
                any(), any(), any());

        verify(auditService).log(eq("creator1"), eq(AuditAction.CREATE_STARTUP), eq("Startup"), eq("startup1"), isNull());
    }

    @Test
    void convertedStartupDefaultsNameToIdeaTitleWhenNoneProvided() {
        Idea idea = idea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));
        when(startupRepository.saveAndFlush(any(Startup.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ideaRepository.save(any(Idea.class))).thenAnswer(inv -> inv.getArgument(0));

        service().convertToStartup("creator1", "idea1", new ConvertToStartupRequest(null, null, null));

        ArgumentCaptor<Startup> captor = ArgumentCaptor.forClass(Startup.class);
        verify(startupRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(idea.getTitle());
        assertThat(captor.getValue().getStage()).isEqualTo(StartupStage.IDEA);
        assertThat(captor.getValue().getIdeaId()).isEqualTo("idea1");
    }

    @Test
    void onlyCreatorCanConvertIdeaToStartup() {
        Idea idea = idea("creator1");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().convertToStartup("stranger1", "idea1", new ConvertToStartupRequest(null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(startupRepository, never()).saveAndFlush(any());
    }

    @Test
    void cannotConvertAnIdeaTwice() {
        Idea idea = idea("creator1");
        idea.setStartupId("existingStartup");
        when(ideaRepository.findById("idea1")).thenReturn(Optional.of(idea));

        assertThatThrownBy(() -> service().convertToStartup("creator1", "idea1", new ConvertToStartupRequest(null, null, null)))
                .isInstanceOf(ConflictException.class);
        verify(startupRepository, never()).saveAndFlush(any());
    }
}
