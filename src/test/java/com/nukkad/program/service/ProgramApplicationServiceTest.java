package com.nukkad.program.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.program.dto.ProgramApplicationDto;
import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramApplication;
import com.nukkad.program.entity.ProgramApplicationStatus;
import com.nukkad.program.mapper.ProgramMapper;
import com.nukkad.program.repository.ProgramApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramApplicationServiceTest {

    @Mock private ProgramApplicationRepository programApplicationRepository;
    private final ProgramMapper programMapper = new ProgramMapper();

    private ProgramApplicationService service() {
        return new ProgramApplicationService(programApplicationRepository, programMapper);
    }

    private ProgramApplication draft(String userId, Program program) {
        return ProgramApplication.builder().id("a1").applicantUserId(userId).program(program)
                .status(ProgramApplicationStatus.DRAFT).answers(new java.util.HashMap<>()).build();
    }

    @Test
    void savingADraftForTheFirstTimeCreatesOne() {
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", Program.SPARK))
                .thenReturn(Optional.empty());
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ProgramApplicationDto dto = service().saveDraft("user1", "spark", Map.of("fullName", "Asha Rao"));

        assertThat(dto.status()).isEqualTo("DRAFT");
        assertThat(dto.answers()).containsEntry("fullName", "Asha Rao");
        ArgumentCaptor<ProgramApplication> captor = ArgumentCaptor.forClass(ProgramApplication.class);
        verify(programApplicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getApplicantUserId()).isEqualTo("user1");
        assertThat(captor.getValue().getProgram()).isEqualTo(Program.SPARK);
    }

    @Test
    void savingADraftTwiceMergesAnswersRatherThanReplacingThem() {
        ProgramApplication existing = draft("user1", Program.SPARK);
        existing.getAnswers().put("fullName", "Asha Rao");
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", Program.SPARK))
                .thenReturn(Optional.of(existing));
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ProgramApplicationDto dto = service().saveDraft("user1", "spark", Map.of("email", "asha@example.com"));

        assertThat(dto.answers()).containsEntry("fullName", "Asha Rao").containsEntry("email", "asha@example.com");
    }

    @Test
    void savingADraftRejectsAnUnknownFieldKey() {
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", Program.SPARK))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().saveDraft("user1", "spark", Map.of("notARealField", "x")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void savingADraftRejectsAMalformedEmail() {
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", Program.SPARK))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().saveDraft("user1", "spark", Map.of("email", "not-an-email")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cannotStartASecondDraftWhileOneIsAlreadySubmitted() {
        ProgramApplication submitted = draft("user1", Program.SPARK);
        submitted.setStatus(ProgramApplicationStatus.SUBMITTED);
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", Program.SPARK))
                .thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> service().saveDraft("user1", "spark", Map.of("fullName", "Asha Rao")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void canStartAFreshDraftAfterAPreviousRejection() {
        ProgramApplication rejected = draft("user1", Program.SPARK);
        rejected.setStatus(ProgramApplicationStatus.REJECTED);
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", Program.SPARK))
                .thenReturn(Optional.of(rejected));
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ProgramApplicationDto dto = service().saveDraft("user1", "spark", Map.of("fullName", "Asha Rao"));

        assertThat(dto.status()).isEqualTo("DRAFT");
    }

    @Test
    void submittingWithMissingRequiredFieldsIsRejectedAndListsWhatIsMissing() {
        ProgramApplication application = draft("user1", Program.SPARK);
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", Program.SPARK))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service().submit("user1", "spark"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Full Name");
    }

    @Test
    void submittingWithEveryRequiredFieldFilledSucceeds() {
        ProgramApplication application = draft("user1", Program.SPARK);
        application.getAnswers().putAll(Map.ofEntries(
                Map.entry("fullName", "Asha Rao"), Map.entry("email", "asha@example.com"), Map.entry("phone", "9876543210"),
                Map.entry("city", "Bengaluru"), Map.entry("dateOfBirth", "2000-01-01"), Map.entry("currentStatus", "Student"),
                Map.entry("educationLevel", "Undergraduate"), Map.entry("institution", "IIT Mandi"), Map.entry("fieldOfStudyOrWork", "CS"),
                Map.entry("currentYear", "3rd Year"), Map.entry("aboutYourself", "I build things."),
                Map.entry("whyJoin", "To explore entrepreneurship."), Map.entry("sixToTwelveMonthGoal", "Launch an idea."),
                Map.entry("excitesYouMost", "Solving real problems."), Map.entry("skillsAndStrengths", "Curiosity and grit.")));
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", Program.SPARK))
                .thenReturn(Optional.of(application));
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ProgramApplicationDto dto = service().submit("user1", "spark");

        assertThat(dto.status()).isEqualTo("SUBMITTED");
        assertThat(dto.submittedAt()).isNotNull();
    }

    @Test
    void cannotSubmitTwice() {
        ProgramApplication submitted = draft("user1", Program.SPARK);
        submitted.setStatus(ProgramApplicationStatus.SUBMITTED);
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", Program.SPARK))
                .thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> service().submit("user1", "spark")).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotSubmitWithoutEverStartingADraft() {
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", Program.SPARK))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().submit("user1", "spark")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aUserCannotWithdrawSomeoneElsesApplication() {
        ProgramApplication other = draft("user2", Program.SPARK);
        other.setStatus(ProgramApplicationStatus.SUBMITTED);
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service().withdraw("user1", "a1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void withdrawingYourOwnSubmittedApplicationSetsItToWithdrawn() {
        ProgramApplication mine = draft("user1", Program.SPARK);
        mine.setStatus(ProgramApplicationStatus.SUBMITTED);
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(mine));
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ProgramApplicationDto dto = service().withdraw("user1", "a1");

        assertThat(dto.status()).isEqualTo("WITHDRAWN");
    }

    @Test
    void cannotWithdrawAnAlreadySelectedApplication() {
        ProgramApplication selected = draft("user1", Program.SPARK);
        selected.setStatus(ProgramApplicationStatus.SELECTED);
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(selected));

        assertThatThrownBy(() -> service().withdraw("user1", "a1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void gettingYourApplicationForAProgramYouNeverStartedIsNotFound() {
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", Program.IGNITE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMineForProgram("user1", "ignite")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void myApplicationsShowsTheLatestRowPerProgramOnly() {
        ProgramApplication oldSpark = draft("user1", Program.SPARK);
        oldSpark.setId("old");
        oldSpark.setStatus(ProgramApplicationStatus.REJECTED);
        ProgramApplication newSpark = draft("user1", Program.SPARK);
        newSpark.setId("new");
        ProgramApplication ignite = draft("user1", Program.IGNITE);
        ignite.setId("ig1");
        when(programApplicationRepository.findByApplicantUserIdOrderByCreatedAtAsc("user1"))
                .thenReturn(List.of(oldSpark, newSpark, ignite));

        List<ProgramApplicationDto> mine = service().getMine("user1");

        assertThat(mine).hasSize(2);
        assertThat(mine).anySatisfy(dto -> assertThat(dto.id()).isEqualTo("new"));
        assertThat(mine).anySatisfy(dto -> assertThat(dto.id()).isEqualTo("ig1"));
    }

    @Test
    void unknownProgramKeyIsRejected() {
        assertThatThrownBy(() -> service().getMineForProgram("user1", "not-a-program"))
                .isInstanceOf(BadRequestException.class);
    }
}
