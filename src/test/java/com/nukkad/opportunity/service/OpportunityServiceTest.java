package com.nukkad.opportunity.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.opportunity.dto.ApplicationDto;
import com.nukkad.opportunity.dto.ApplyToOpportunityRequest;
import com.nukkad.opportunity.dto.OpportunityDto;
import com.nukkad.opportunity.dto.PostOpportunityRequest;
import com.nukkad.opportunity.entity.ApplicationStatus;
import com.nukkad.opportunity.entity.Opportunity;
import com.nukkad.opportunity.entity.OpportunityApplicant;
import com.nukkad.opportunity.mapper.OpportunityMapper;
import com.nukkad.opportunity.repository.OpportunityApplicantRepository;
import com.nukkad.opportunity.repository.OpportunityInterestRepository;
import com.nukkad.opportunity.repository.OpportunityRepository;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.Availability;
import com.nukkad.user.entity.User;
import com.nukkad.user.entity.UserExperience;
import com.nukkad.user.entity.UserProject;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.UserExperienceRepository;
import com.nukkad.user.repository.UserProjectRepository;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Opportunity Application workflow's state machine, authorization, duplicate/withdrawal
 * handling and notification content. Messaging-permission behavior driven by an accepted application
 * (does it unlock messaging, does it avoid creating a real Connection, does existing connection-based
 * messaging keep working) is covered separately in {@code ConversationServiceTest}, since that logic
 * lives in {@code ConversationService}, not here.
 */
@ExtendWith(MockitoExtension.class)
class OpportunityServiceTest {

    private static final List<StartupTeamMember.TeamRole> MANAGER_ROLES =
            List.of(StartupTeamMember.TeamRole.FOUNDER, StartupTeamMember.TeamRole.ADMIN);

    @Mock private OpportunityRepository opportunityRepository;
    @Mock private OpportunityApplicantRepository applicantRepository;
    @Mock private OpportunityInterestRepository interestRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserExperienceRepository userExperienceRepository;
    @Mock private UserProjectRepository userProjectRepository;
    @Mock private UserService userService;
    @Mock private UserMapper userMapper;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private StartupTeamMemberRepository startupTeamMemberRepository;

    private OpportunityService service() {
        return new OpportunityService(opportunityRepository, applicantRepository, interestRepository,
                userRepository, userExperienceRepository, userProjectRepository, userService, userMapper,
                opportunityMapper, notificationService, auditService, startupTeamMemberRepository);
    }

    private Opportunity opportunity(String posterId) {
        return Opportunity.builder().id("opp1").title("Product Designer").postedByUserId(posterId)
                .moderationStatus(com.nukkad.common.moderation.ModerationStatus.APPROVED).build();
    }

    private OpportunityApplicant applicant(String opportunityId, String userId, ApplicationStatus status) {
        return OpportunityApplicant.builder().id("app1").opportunityId(opportunityId).userId(userId).status(status).build();
    }

    private User user(String id, String name) {
        return User.builder().id(id).name(name).skills(new HashSet<>()).build();
    }

    private UserDto stubUserDto(String id) {
        return new UserDto(id, id, null, null, null, null, null, null, null, 0,
                Set.of(), Set.of(), Set.of(), Map.of(), null, null, null, null, 0, false, null,
                null, null, null, null, null, null, null, null, null, null, null, Set.of(), false, true);
    }

    // ---- 1. Successful application creation ----

    @Test
    void appliesSuccessfullyAndNotifiesPoster() {
        Opportunity opp = opportunity("owner1");
        User applicantUser = user("applicant1", "Meera Joshi");
        applicantUser.getSkills().addAll(Set.of("Figma", "UX"));
        applicantUser.setAvailability(Availability.PART_TIME);

        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(applicantUser));
        when(applicantRepository.findByOpportunityIdAndUserId("opp1", "applicant1")).thenReturn(Optional.empty());
        when(applicantRepository.saveAndFlush(any(OpportunityApplicant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getUser("applicant1", "applicant1")).thenReturn(stubUserDto("applicant1"));

        ApplyToOpportunityRequest request = new ApplyToOpportunityRequest(
                "Because I love design", "I have 3 years of UX experience",
                null, null, null, null, "10 hrs/week", "Looking forward to it");

        ApplicationDto dto = service().apply("applicant1", "opp1", request);

        assertThat(dto.status()).isEqualTo("Pending");
        assertThat(dto.whyInterested()).isEqualTo("Because I love design");
        assertThat(dto.relevantSkills()).containsExactlyInAnyOrder("Figma", "UX");
        assertThat(dto.availability()).isEqualTo("Part-time");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq("owner1"), eq(NotificationType.opportunity), eq("New application"),
                message.capture(), eq("opp1"), eq("applicant1"));
        assertThat(message.getValue()).contains("Meera Joshi").contains("Product Designer");
    }

    @Test
    void applyingAlsoNotifiesTheApplicantThemselvesThatItWasSubmitted() {
        Opportunity opp = opportunity("owner1");
        User applicantUser = user("applicant1", "Meera Joshi");

        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(applicantUser));
        when(applicantRepository.findByOpportunityIdAndUserId("opp1", "applicant1")).thenReturn(Optional.empty());
        when(applicantRepository.saveAndFlush(any(OpportunityApplicant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getUser("applicant1", "applicant1")).thenReturn(stubUserDto("applicant1"));

        ApplyToOpportunityRequest request = new ApplyToOpportunityRequest(
                "Because I love design", "I have 3 years of UX experience", null, null, null, null, null, null);

        service().apply("applicant1", "opp1", request);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq("applicant1"), eq(NotificationType.opportunity), eq("Application submitted"),
                message.capture(), eq("opp1"), isNull());
        assertThat(message.getValue()).contains("Product Designer");
    }

    @Test
    void applyFiltersOutExperienceAndProjectIdsNotOwnedByApplicant() {
        Opportunity opp = opportunity("owner1");
        User applicantUser = user("applicant1", "Meera Joshi");

        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(applicantUser));
        when(applicantRepository.findByOpportunityIdAndUserId("opp1", "applicant1")).thenReturn(Optional.empty());
        when(userExperienceRepository.findByUser_IdOrderBySortOrderAsc("applicant1"))
                .thenReturn(List.of(UserExperience.builder().id("exp-owned").build()));
        when(userProjectRepository.findByUser_IdOrderBySortOrderAsc("applicant1"))
                .thenReturn(List.of(UserProject.builder().id("proj-owned").build()));
        when(applicantRepository.saveAndFlush(any(OpportunityApplicant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getUser("applicant1", "applicant1")).thenReturn(stubUserDto("applicant1"));

        ApplyToOpportunityRequest request = new ApplyToOpportunityRequest("x", "y", null,
                List.of("exp-owned", "exp-not-owned"), List.of("proj-owned", "proj-not-owned"), null, null, null);

        service().apply("applicant1", "opp1", request);

        ArgumentCaptor<OpportunityApplicant> captor = ArgumentCaptor.forClass(OpportunityApplicant.class);
        verify(applicantRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getExperienceIds()).containsExactly("exp-owned");
        assertThat(captor.getValue().getProjectIds()).containsExactly("proj-owned");
    }

    // ---- 2. Required-field validation ----

    @Test
    void applyRequestRejectsBlankRequiredFields() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ApplyToOpportunityRequest blank = new ApplyToOpportunityRequest("", "", null, null, null, null, null, null);

        var violations = validator.validate(blank);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("whyInterested", "whyGoodFit");
    }

    @Test
    void applyRequestAcceptsPopulatedRequiredFields() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ApplyToOpportunityRequest valid = new ApplyToOpportunityRequest(
                "Because this excites me", "I've shipped similar products", null, null, null, null, null, null);

        assertThat(validator.validate(valid)).isEmpty();
    }

    // ---- 3. Duplicate application prevention ----

    @Test
    void duplicateApplicationIsRejectedWhenNotWithdrawn() {
        Opportunity opp = opportunity("owner1");
        OpportunityApplicant existing = applicant("opp1", "applicant1", ApplicationStatus.PENDING);

        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(user("applicant1", "Meera")));
        when(applicantRepository.findByOpportunityIdAndUserId("opp1", "applicant1")).thenReturn(Optional.of(existing));

        ApplyToOpportunityRequest request = new ApplyToOpportunityRequest("x", "y", null, null, null, null, null, null);

        assertThatThrownBy(() -> service().apply("applicant1", "opp1", request))
                .isInstanceOf(BadRequestException.class);

        verify(applicantRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    // ---- 4. Re-apply after withdrawal ----

    @Test
    void reapplyingAfterWithdrawalReusesSameRowAndResetsToPending() {
        Opportunity opp = opportunity("owner1");
        OpportunityApplicant existing = applicant("opp1", "applicant1", ApplicationStatus.WITHDRAWN);
        existing.setReviewedAt(Instant.now());

        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(user("applicant1", "Meera")));
        when(applicantRepository.findByOpportunityIdAndUserId("opp1", "applicant1")).thenReturn(Optional.of(existing));
        when(applicantRepository.saveAndFlush(any(OpportunityApplicant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getUser("applicant1", "applicant1")).thenReturn(stubUserDto("applicant1"));

        ApplyToOpportunityRequest request = new ApplyToOpportunityRequest("Trying again", "Still a good fit",
                Set.of("Java"), null, null, "Full-time", "20 hrs/week", null);

        ApplicationDto dto = service().apply("applicant1", "opp1", request);

        assertThat(dto.status()).isEqualTo("Pending");

        ArgumentCaptor<OpportunityApplicant> captor = ArgumentCaptor.forClass(OpportunityApplicant.class);
        verify(applicantRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
        assertThat(captor.getValue().getReviewedAt()).isNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    // ---- 5. Applicant can withdraw a pending application ----

    @Test
    void applicantCanWithdrawPendingApplication() {
        Opportunity opp = opportunity("owner1");
        OpportunityApplicant existing = applicant("opp1", "applicant1", ApplicationStatus.PENDING);

        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(applicantRepository.findByOpportunityIdAndUserId("opp1", "applicant1")).thenReturn(Optional.of(existing));
        when(applicantRepository.saveAndFlush(any(OpportunityApplicant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("applicant1")).thenReturn(Optional.of(user("applicant1", "Meera Joshi")));

        service().withdrawApplication("applicant1", "opp1");

        assertThat(existing.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(existing.getReviewedAt()).isNotNull();

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq("owner1"), eq(NotificationType.opportunity), eq("Application withdrawn"),
                message.capture(), eq("opp1"), eq("applicant1"));
        assertThat(message.getValue()).contains("Meera Joshi");
    }

    // ---- 6. Applicant cannot withdraw terminal applications ----

    @Test
    void withdrawingTerminalApplicationsIsRejected() {
        for (ApplicationStatus terminal : List.of(ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN)) {
            Opportunity opp = opportunity("owner1");
            OpportunityApplicant existing = applicant("opp1", "applicant1", terminal);
            when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
            when(applicantRepository.findByOpportunityIdAndUserId("opp1", "applicant1")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service().withdrawApplication("applicant1", "opp1"))
                    .isInstanceOf(BadRequestException.class);
        }

        verify(applicantRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    // ---- 7. Only opportunity owner can list applications ----

    @Test
    void nonOwnerCannotListApplications() {
        Opportunity opp = opportunity("owner1");
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));

        assertThatThrownBy(() -> service().listApplications("stranger1", "opp1", null, 0, 20))
                .isInstanceOf(ForbiddenException.class);

        verify(applicantRepository, never()).findByOpportunityIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void ownerCanListApplicationsWithOptionalStatusFilter() {
        Opportunity opp = opportunity("owner1");
        OpportunityApplicant app = applicant("opp1", "applicant1", ApplicationStatus.SHORTLISTED);

        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(applicantRepository.findByOpportunityIdAndStatusOrderByCreatedAtDesc(
                eq("opp1"), eq(ApplicationStatus.SHORTLISTED), any())).thenReturn(new PageImpl<>(List.of(app)));
        when(userService.getUser("applicant1", "owner1")).thenReturn(stubUserDto("applicant1"));

        Page<ApplicationDto> page = service().listApplications("owner1", "opp1", "Shortlisted", 0, 20);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).status()).isEqualTo("Shortlisted");
    }

    // ---- 8. Applicant/owner authorization for application details ----

    @Test
    void applicantAndOwnerCanViewApplicationDetailButOthersCannot() {
        Opportunity opp = opportunity("owner1");
        OpportunityApplicant app = applicant("opp1", "applicant1", ApplicationStatus.PENDING);

        when(applicantRepository.findById("app1")).thenReturn(Optional.of(app));
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(userService.getUser("applicant1", "applicant1")).thenReturn(stubUserDto("applicant1"));
        when(userService.getUser("applicant1", "owner1")).thenReturn(stubUserDto("applicant1"));

        assertThat(service().getApplication("applicant1", "app1").id()).isEqualTo("app1");
        assertThat(service().getApplication("owner1", "app1").id()).isEqualTo("app1");

        assertThatThrownBy(() -> service().getApplication("stranger1", "app1"))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- 9. Shortlist transition ----

    @Test
    void shortlistTransitionSetsStatusAndNotifiesApplicant() {
        Opportunity opp = opportunity("owner1");
        OpportunityApplicant app = applicant("opp1", "applicant1", ApplicationStatus.PENDING);

        when(applicantRepository.findById("app1")).thenReturn(Optional.of(app));
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(applicantRepository.saveAndFlush(any(OpportunityApplicant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("owner1")).thenReturn(Optional.of(user("owner1", "Rishi")));
        when(userService.getUser("applicant1", "owner1")).thenReturn(stubUserDto("applicant1"));

        ApplicationDto dto = service().shortlistApplication("owner1", "app1");

        assertThat(dto.status()).isEqualTo("Shortlisted");
        assertThat(app.getReviewedAt()).isNotNull();

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq("applicant1"), eq(NotificationType.opportunity), eq("Application shortlisted"),
                message.capture(), eq("opp1"), eq("owner1"));
        assertThat(message.getValue()).contains("Rishi").contains("Product Designer");
    }

    // ---- 10. Accept transition ----

    @Test
    void acceptTransitionNotifiesApplicantThatMessagingIsUnlocked() {
        Opportunity opp = opportunity("owner1");
        OpportunityApplicant app = applicant("opp1", "applicant1", ApplicationStatus.SHORTLISTED);

        when(applicantRepository.findById("app1")).thenReturn(Optional.of(app));
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(applicantRepository.saveAndFlush(any(OpportunityApplicant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("owner1")).thenReturn(Optional.of(user("owner1", "Rishi")));
        when(userService.getUser("applicant1", "owner1")).thenReturn(stubUserDto("applicant1"));

        ApplicationDto dto = service().acceptApplication("owner1", "app1");

        assertThat(dto.status()).isEqualTo("Accepted");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq("applicant1"), eq(NotificationType.opportunity), eq("Application accepted!"),
                message.capture(), eq("opp1"), eq("owner1"));
        assertThat(message.getValue()).contains("you can now message them");
    }

    // ---- 11. Reject transition ----

    @Test
    void rejectTransitionSetsStatusAndNotifiesApplicant() {
        Opportunity opp = opportunity("owner1");
        OpportunityApplicant app = applicant("opp1", "applicant1", ApplicationStatus.PENDING);

        when(applicantRepository.findById("app1")).thenReturn(Optional.of(app));
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(applicantRepository.saveAndFlush(any(OpportunityApplicant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("owner1")).thenReturn(Optional.of(user("owner1", "Rishi")));
        when(userService.getUser("applicant1", "owner1")).thenReturn(stubUserDto("applicant1"));

        ApplicationDto dto = service().rejectApplication("owner1", "app1");

        assertThat(dto.status()).isEqualTo("Rejected");
        verify(notificationService).notify(eq("applicant1"), eq(NotificationType.opportunity), eq("Application update"),
                any(), eq("opp1"), eq("owner1"));
    }

    // ---- 12. Terminal-state protection (and non-owner rejection) ----

    @Test
    void transitionsOnTerminalApplicationsAreRejected() {
        Opportunity opp = opportunity("owner1");
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));

        for (ApplicationStatus terminal : List.of(ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN)) {
            when(applicantRepository.findById("app1")).thenReturn(Optional.of(applicant("opp1", "applicant1", terminal)));

            OpportunityService service = service();
            assertThatThrownBy(() -> service.shortlistApplication("owner1", "app1")).isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service.acceptApplication("owner1", "app1")).isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service.rejectApplication("owner1", "app1")).isInstanceOf(BadRequestException.class);
        }

        verify(applicantRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    void nonOwnerCannotTransitionApplicationStatus() {
        Opportunity opp = opportunity("owner1");
        OpportunityApplicant app = applicant("opp1", "applicant1", ApplicationStatus.PENDING);

        when(applicantRepository.findById("app1")).thenReturn(Optional.of(app));
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));

        assertThatThrownBy(() -> service().shortlistApplication("stranger1", "app1"))
                .isInstanceOf(ForbiddenException.class);

        verify(applicantRepository, never()).saveAndFlush(any());
    }

    // ---- 13. Posting is gated to founders of a startup ----

    private PostOpportunityRequest postRequest() {
        return new PostOpportunityRequest("AI/ML Intern", "Internship", null, "ABC Technologies",
                "Bengaluru", "Remote", "Build ML pipelines", null, List.of("Python"), null, null, null, null, null);
    }

    @Test
    void nonFounderCannotPostAnOpportunity() {
        when(userRepository.findById("user1")).thenReturn(Optional.of(user("user1", "Alex")));
        when(startupTeamMemberRepository.existsByUserIdAndTeamRoleInAndStatus("user1", MANAGER_ROLES, StartupTeamMember.Status.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> service().postOpportunity("user1", postRequest()))
                .isInstanceOf(ForbiddenException.class);

        verify(opportunityRepository, never()).saveAndFlush(any());
    }

    @Test
    void founderOfAStartupCanPostAnOpportunity() {
        when(startupTeamMemberRepository.existsByUserIdAndTeamRoleInAndStatus("founder1", MANAGER_ROLES, StartupTeamMember.Status.ACTIVE))
                .thenReturn(true);
        when(userRepository.findById("founder1")).thenReturn(Optional.of(user("founder1", "Rishi")));
        when(opportunityRepository.saveAndFlush(any(Opportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        service().postOpportunity("founder1", postRequest());

        verify(opportunityRepository).saveAndFlush(any(Opportunity.class));
    }

    @Test
    void founderCannotAttributeAnOpportunityToAStartupTheyDidNotFound() {
        // Regression test: a founder of Startup A could previously set startupId to Startup B's
        // id with no check that they actually founded B.
        PostOpportunityRequest requestForSomeoneElsesStartup = new PostOpportunityRequest(
                "AI/ML Intern", "Internship", "startupB", "ABC Technologies", "Bengaluru", "Remote",
                "Build ML pipelines", null, List.of("Python"), null, null, null, null, null);
        when(startupTeamMemberRepository.existsByUserIdAndTeamRoleInAndStatus("founder1", MANAGER_ROLES, StartupTeamMember.Status.ACTIVE))
                .thenReturn(true);
        when(userRepository.findById("founder1")).thenReturn(Optional.of(user("founder1", "Rishi")));
        when(startupTeamMemberRepository.existsByStartupIdAndUserIdAndTeamRoleInAndStatus(
                "startupB", "founder1", MANAGER_ROLES, StartupTeamMember.Status.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> service().postOpportunity("founder1", requestForSomeoneElsesStartup))
                .isInstanceOf(ForbiddenException.class);

        verify(opportunityRepository, never()).saveAndFlush(any());
    }

    // ---- 14. Closed opportunities reject new applications and interest ----

    @Test
    void applyingToAClosedOpportunityIsRejected() {
        Opportunity opp = opportunity("owner1");
        opp.setClosed(true);
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));

        ApplyToOpportunityRequest request = new ApplyToOpportunityRequest("x", "y", null, null, null, null, null, null);

        assertThatThrownBy(() -> service().apply("applicant1", "opp1", request))
                .isInstanceOf(BadRequestException.class);

        verify(applicantRepository, never()).saveAndFlush(any());
    }

    @Test
    void expressingInterestInAClosedOpportunityIsRejected() {
        Opportunity opp = opportunity("owner1");
        opp.setClosed(true);
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));

        assertThatThrownBy(() -> service().expressInterest("applicant1", "opp1"))
                .isInstanceOf(BadRequestException.class);

        verify(interestRepository, never()).save(any());
    }

    @Test
    void historicalApplicationsRemainAccessibleAfterOpportunityIsClosed() {
        Opportunity opp = opportunity("owner1");
        opp.setClosed(true);
        OpportunityApplicant app = applicant("opp1", "applicant1", ApplicationStatus.PENDING);

        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(applicantRepository.findById("app1")).thenReturn(Optional.of(app));
        when(applicantRepository.findByOpportunityIdOrderByCreatedAtDesc(eq("opp1"), any()))
                .thenReturn(new PageImpl<>(List.of(app)));
        when(userService.getUser("applicant1", "owner1")).thenReturn(stubUserDto("applicant1"));
        when(userService.getUser("applicant1", "applicant1")).thenReturn(stubUserDto("applicant1"));

        // Founder can still list and open the application for a now-closed opportunity.
        Page<ApplicationDto> page = service().listApplications("owner1", "opp1", null, 0, 20);
        assertThat(page.getContent()).hasSize(1);
        assertThat(service().getApplication("owner1", "app1").id()).isEqualTo("app1");

        // The applicant can still open their own historical application too.
        assertThat(service().getApplication("applicant1", "app1").id()).isEqualTo("app1");
    }

    @Test
    void historicalApplicationsRemainAccessibleAcrossAllTerminalStatusesAfterClosure() {
        Opportunity opp = opportunity("owner1");
        opp.setClosed(true);
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));

        for (ApplicationStatus status : List.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.ACCEPTED,
                ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN)) {
            String appId = "app-" + status;
            String userId = "applicant-" + status;
            OpportunityApplicant app = OpportunityApplicant.builder()
                    .id(appId).opportunityId("opp1").userId(userId).status(status).build();
            when(applicantRepository.findById(appId)).thenReturn(Optional.of(app));
            when(userService.getUser(userId, "owner1")).thenReturn(stubUserDto(userId));
            when(userService.getUser(userId, userId)).thenReturn(stubUserDto(userId));

            assertThat(service().getApplication("owner1", appId).status()).isEqualTo(status.getLabel());
            assertThat(service().getApplication(userId, appId).status()).isEqualTo(status.getLabel());
        }
    }

    @Test
    void listMyApplicationsIncludesOpportunitiesThatHaveSinceBeenClosed() {
        Opportunity closedOpp = opportunity("owner1");
        closedOpp.setClosed(true);
        OpportunityApplicant app = OpportunityApplicant.builder()
                .id("app1").opportunityId("opp1").userId("applicant1").status(ApplicationStatus.ACCEPTED).build();

        when(applicantRepository.findByUserId("applicant1")).thenReturn(List.of(app));
        when(interestRepository.findByUserId("applicant1")).thenReturn(List.of());
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(closedOpp));
        when(applicantRepository.findByOpportunityIdAndUserId("opp1", "applicant1")).thenReturn(Optional.of(app));
        when(interestRepository.existsByOpportunityIdAndUserId("opp1", "applicant1")).thenReturn(false);
        when(applicantRepository.countByOpportunityIdAndStatusNotIn(
                "opp1", List.of(ApplicationStatus.WITHDRAWN, ApplicationStatus.REJECTED))).thenReturn(1L);
        when(interestRepository.countByOpportunityId("opp1")).thenReturn(0L);
        when(opportunityMapper.toDto(eq(closedOpp), anyBoolean(), anyBoolean(), any(), anyInt(), anyInt(), any()))
                .thenAnswer(inv -> new OpportunityDto(
                        closedOpp.getId(),           // id
                        closedOpp.getTitle(),         // title
                        null,                          // type
                        closedOpp.isClosed(),         // closed
                        false,                         // removedByAdmin
                        null,                          // removalReason
                        null,                          // moderationStatus
                        null,                          // rejectionReason
                        null,                          // startupId
                        null,                          // organizationName
                        null,                          // location
                        null,                          // workMode
                        null,                          // description
                        null,                          // responsibilities
                        null,                          // compensation
                        null,                          // equity
                        null,                          // experienceLevel
                        null,                          // applicationDeadline
                        closedOpp.getPostedByUserId(), // postedByUserId
                        null,                          // chapterId
                        List.of(),                     // requirements
                        List.of(),                     // requiredSkills
                        true,                           // hasApplied
                        false,                          // hasExpressedInterest
                        "Accepted",                     // applicationStatus
                        1,                              // applicantCount
                        0,                              // interestCount
                        null,                           // appliedAt
                        null,                           // createdAt
                        null));                         // updatedAt

        // A closed opportunity must still show up in the candidate's own application history —
        // no "open only" filter should ever leak into this query.
        Page<OpportunityDto> page = service().listMyApplications("applicant1", 0, 20);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).id()).isEqualTo("opp1");
        assertThat(page.getContent().get(0).closed()).isTrue();
    }

    // ---- Poster cannot apply/express interest in their own opportunity ----

    @Test
    void posterCannotApplyToTheirOwnOpportunity() {
        Opportunity opp = opportunity("owner1");
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));

        ApplyToOpportunityRequest request = new ApplyToOpportunityRequest("x", "y", null, null, null, null, null, null);

        assertThatThrownBy(() -> service().apply("owner1", "opp1", request))
                .isInstanceOf(BadRequestException.class);

        verify(applicantRepository, never()).saveAndFlush(any());
    }

    @Test
    void posterCannotExpressInterestInTheirOwnOpportunity() {
        Opportunity opp = opportunity("owner1");
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));

        assertThatThrownBy(() -> service().expressInterest("owner1", "opp1"))
                .isInstanceOf(BadRequestException.class);

        verify(interestRepository, never()).save(any());
    }

    // ---- 15. Close/reopen is owner-only ----

    @Test
    void ownerCanCloseAndReopenTheirOpportunity() {
        Opportunity opp = opportunity("owner1");
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(opportunityRepository.saveAndFlush(any(Opportunity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicantRepository.findByOpportunityId("opp1")).thenReturn(List.of());

        service().closeOpportunity("owner1", "opp1");
        assertThat(opp.isClosed()).isTrue();

        service().reopenOpportunity("owner1", "opp1");
        assertThat(opp.isClosed()).isFalse();
    }

    @Test
    void nonOwnerCannotCloseAnOpportunity() {
        Opportunity opp = opportunity("owner1");
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));

        assertThatThrownBy(() -> service().closeOpportunity("stranger1", "opp1"))
                .isInstanceOf(ForbiddenException.class);

        verify(opportunityRepository, never()).saveAndFlush(any());
    }

    // ---- Closing an opportunity notifies applicants still awaiting a decision ----

    @Test
    void closingAnOpenOpportunityNotifiesOnlyNonTerminalApplicants() {
        Opportunity opp = opportunity("owner1");
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(opportunityRepository.saveAndFlush(any(Opportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        OpportunityApplicant pending = applicant("opp1", "pendingUser", ApplicationStatus.PENDING);
        OpportunityApplicant shortlisted = applicant("opp1", "shortlistedUser", ApplicationStatus.SHORTLISTED);
        OpportunityApplicant accepted = applicant("opp1", "acceptedUser", ApplicationStatus.ACCEPTED);
        OpportunityApplicant rejected = applicant("opp1", "rejectedUser", ApplicationStatus.REJECTED);
        OpportunityApplicant withdrawn = applicant("opp1", "withdrawnUser", ApplicationStatus.WITHDRAWN);
        when(applicantRepository.findByOpportunityId("opp1"))
                .thenReturn(List.of(pending, shortlisted, accepted, rejected, withdrawn));

        service().closeOpportunity("owner1", "opp1");

        verify(notificationService).notify(eq("pendingUser"), eq(NotificationType.opportunity),
                eq("Opportunity closed"), any(), eq("opp1"), eq("owner1"));
        verify(notificationService).notify(eq("shortlistedUser"), eq(NotificationType.opportunity),
                eq("Opportunity closed"), any(), eq("opp1"), eq("owner1"));
        verify(notificationService, never()).notify(eq("acceptedUser"), any(), any(), any(), any(), any());
        verify(notificationService, never()).notify(eq("rejectedUser"), any(), any(), any(), any(), any());
        verify(notificationService, never()).notify(eq("withdrawnUser"), any(), any(), any(), any(), any());
    }

    @Test
    void closingAnAlreadyClosedOpportunityDoesNotReNotifyApplicants() {
        Opportunity opp = opportunity("owner1");
        opp.setClosed(true);
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(opportunityRepository.saveAndFlush(any(Opportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        service().closeOpportunity("owner1", "opp1");

        verify(applicantRepository, never()).findByOpportunityId(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    // ---- Applicant count reflects only live applications, not withdrawn/rejected ones ----

    @Test
    void applicantCountExcludesWithdrawnAndRejectedApplications() {
        Opportunity opp = opportunity("owner1");
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(applicantRepository.findByOpportunityIdAndUserId("opp1", "viewer1")).thenReturn(Optional.empty());
        when(interestRepository.existsByOpportunityIdAndUserId("opp1", "viewer1")).thenReturn(false);
        when(applicantRepository.countByOpportunityIdAndStatusNotIn(
                "opp1", List.of(ApplicationStatus.WITHDRAWN, ApplicationStatus.REJECTED))).thenReturn(2L);
        when(interestRepository.countByOpportunityId("opp1")).thenReturn(0L);

        service().getOpportunity("opp1", "viewer1");

        verify(applicantRepository).countByOpportunityIdAndStatusNotIn(
                "opp1", List.of(ApplicationStatus.WITHDRAWN, ApplicationStatus.REJECTED));
        verify(applicantRepository, never()).countByOpportunityId("opp1");
    }

    // ---- Admin moderation ----

    @Test
    void publicGetterThrowsNotFoundForARemovedOpportunity() {
        Opportunity opp = opportunity("owner1");
        opp.setRemovedByAdmin(true);
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));

        assertThatThrownBy(() -> service().getOpportunity("opp1", "viewer1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminGetterStillReturnsARemovedOpportunityWithoutThrowing() {
        Opportunity opp = opportunity("owner1");
        opp.setRemovedByAdmin(true);
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(applicantRepository.findByOpportunityIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(interestRepository.existsByOpportunityIdAndUserId(any(), any())).thenReturn(false);
        when(applicantRepository.countByOpportunityIdAndStatusNotIn(any(), any())).thenReturn(0L);
        when(interestRepository.countByOpportunityId(any())).thenReturn(0L);

        service().getOpportunityForAdmin("opp1", "admin1");

        verify(opportunityMapper).toDto(eq(opp), anyBoolean(), anyBoolean(), any(), anyInt(), anyInt(), any());
    }

    @Test
    void adminCanRemoveAndRestoreAnOpportunityWithAudit() {
        Opportunity opp = opportunity("owner1");
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(opportunityRepository.saveAndFlush(any(Opportunity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicantRepository.findByOpportunityIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(interestRepository.existsByOpportunityIdAndUserId(any(), any())).thenReturn(false);
        when(applicantRepository.countByOpportunityIdAndStatusNotIn(any(), any())).thenReturn(0L);
        when(interestRepository.countByOpportunityId(any())).thenReturn(0L);

        service().setRemovedByAdmin("admin1", "opp1", true, "Scam", "5.5.5.5");
        assertThat(opp.isRemovedByAdmin()).isTrue();
        assertThat(opp.getRemovalReason()).isEqualTo("Scam");
        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_CONTENT_REMOVED), eq("Opportunity"), eq("opp1"), eq("5.5.5.5"), any());

        service().setRemovedByAdmin("admin1", "opp1", false, null, "5.5.5.5");
        assertThat(opp.isRemovedByAdmin()).isFalse();
        assertThat(opp.getRemovalReason()).isNull();
        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_CONTENT_RESTORED), eq("Opportunity"), eq("opp1"), eq("5.5.5.5"), any());
    }

    // ---- Pre-publish moderation ----

    private Opportunity pendingOpportunity(String posterId) {
        Opportunity opp = opportunity(posterId);
        opp.setModerationStatus(com.nukkad.common.moderation.ModerationStatus.PENDING);
        return opp;
    }

    @Test
    void publicGetterHidesAPendingOpportunityFromEveryoneButItsPoster() {
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(pendingOpportunity("owner1")));

        assertThatThrownBy(() -> service().getOpportunity("opp1", "viewer1")).isInstanceOf(ResourceNotFoundException.class);
    }

    /** {@code opportunityMapper} is a bare mock (unlike ideaMapper/startupMapper in the sibling test
     *  files), so any test that inspects the returned DTO's moderationStatus needs this stub. */
    private void stubMapperReflectingModerationStatus() {
        when(opportunityMapper.toDto(any(Opportunity.class), anyBoolean(), anyBoolean(), any(), anyInt(), anyInt(), any()))
                .thenAnswer(inv -> {
                    Opportunity o = inv.getArgument(0);
                    return new OpportunityDto(o.getId(), o.getTitle(), null, o.isClosed(), o.isRemovedByAdmin(),
                            o.getRemovalReason(), o.getModerationStatus().name(), o.getRejectionReason(),
                            o.getStartupId(), o.getOrganizationName(), null, null, null, null, null, null, null, null,
                            o.getPostedByUserId(), null, List.of(), List.of(), false, false, null, 0, 0, null, null, null);
                });
    }

    @Test
    void publicGetterStillShowsAPendingOpportunityToItsPoster() {
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(pendingOpportunity("owner1")));
        when(applicantRepository.findByOpportunityIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(interestRepository.existsByOpportunityIdAndUserId(any(), any())).thenReturn(false);
        when(applicantRepository.countByOpportunityIdAndStatusNotIn(any(), any())).thenReturn(0L);
        when(interestRepository.countByOpportunityId(any())).thenReturn(0L);
        stubMapperReflectingModerationStatus();

        OpportunityDto dto = service().getOpportunity("opp1", "owner1");

        assertThat(dto.moderationStatus()).isEqualTo("PENDING");
    }

    @Test
    void approvingAPendingOpportunityLogsAuditAndNotifiesThePoster() {
        Opportunity opp = pendingOpportunity("owner1");
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opp));
        when(opportunityRepository.saveAndFlush(any(Opportunity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicantRepository.findByOpportunityIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(interestRepository.existsByOpportunityIdAndUserId(any(), any())).thenReturn(false);
        when(applicantRepository.countByOpportunityIdAndStatusNotIn(any(), any())).thenReturn(0L);
        when(interestRepository.countByOpportunityId(any())).thenReturn(0L);
        stubMapperReflectingModerationStatus();

        OpportunityDto dto = service().reviewModeration("admin1", "opp1", true, null, "1.2.3.4");

        assertThat(dto.moderationStatus()).isEqualTo("APPROVED");
        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_CONTENT_APPROVED), eq("Opportunity"), eq("opp1"), eq("1.2.3.4"), any());
        verify(notificationService).notify(eq("owner1"), any(), anyString(), anyString(), eq("opp1"), eq("admin1"));
    }

    @Test
    void rejectingAPendingOpportunityRequiresAReason() {
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(pendingOpportunity("owner1")));

        assertThatThrownBy(() -> service().reviewModeration("admin1", "opp1", false, null, "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void anAlreadyReviewedOpportunityCannotBeReviewedAgain() {
        when(opportunityRepository.findById("opp1")).thenReturn(Optional.of(opportunity("owner1"))); // fixture defaults to APPROVED

        assertThatThrownBy(() -> service().reviewModeration("admin1", "opp1", true, null, "1.2.3.4"))
                .isInstanceOf(ConflictException.class);
    }
}
