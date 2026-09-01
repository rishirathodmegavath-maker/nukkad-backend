package com.nukkad.opportunity.service;

import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.opportunity.dto.ApplicationDto;
import com.nukkad.opportunity.dto.ApplyToOpportunityRequest;
import com.nukkad.opportunity.entity.ApplicationStatus;
import com.nukkad.opportunity.entity.Opportunity;
import com.nukkad.opportunity.entity.OpportunityApplicant;
import com.nukkad.opportunity.mapper.OpportunityMapper;
import com.nukkad.opportunity.repository.OpportunityApplicantRepository;
import com.nukkad.opportunity.repository.OpportunityInterestRepository;
import com.nukkad.opportunity.repository.OpportunityRepository;
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
import static org.mockito.ArgumentMatchers.eq;
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

    private OpportunityService service() {
        return new OpportunityService(opportunityRepository, applicantRepository, interestRepository,
                userRepository, userExperienceRepository, userProjectRepository, userService, userMapper,
                opportunityMapper, notificationService, auditService);
    }

    private Opportunity opportunity(String posterId) {
        return Opportunity.builder().id("opp1").title("Product Designer").postedByUserId(posterId).build();
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
                null, null, null, null, null, null, null, null, null, null, null, Set.of(), false);
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
}
