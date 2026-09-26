package com.nukkad.program.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.program.catalog.ProgramContentCodec;
import com.nukkad.program.catalog.ProgramField;
import com.nukkad.program.catalog.ProgramFieldType;
import com.nukkad.program.catalog.ProgramStep;
import com.nukkad.program.dto.ProgramApplicationDto;
import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramApplication;
import com.nukkad.program.entity.ProgramApplicationStatus;
import com.nukkad.program.entity.ProgramStatus;
import com.nukkad.program.mapper.ProgramMapper;
import com.nukkad.program.repository.ProgramApplicationRepository;
import com.nukkad.program.repository.ProgramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Uses a small, purpose-built test program rather than mirroring SPARK/IGNITE's real content: this
 *  is testing the draft/submit/withdraw/validation logic itself, which should not need to change (or
 *  risk drifting out of sync) every time Admin edits a real program's real questions now that they're
 *  admin-editable rather than fixed in code. */
@ExtendWith(MockitoExtension.class)
class ProgramApplicationServiceTest {

    @Mock private ProgramApplicationRepository programApplicationRepository;
    @Mock private ProgramRepository programRepository;
    private final ProgramContentCodec codec = new ProgramContentCodec(new ObjectMapper());
    private final ProgramMapper programMapper = new ProgramMapper(codec);

    private ProgramApplicationService service() {
        return new ProgramApplicationService(programApplicationRepository, programRepository, codec, programMapper);
    }

    private Program testProgram(String slug, ProgramStatus status) {
        List<ProgramStep> steps = List.of(
                new ProgramStep("basic-information", "Basic Information", List.of(
                        new ProgramField("fullName", "Full Name", ProgramFieldType.TEXT, true, List.of()),
                        new ProgramField("email", "Email", ProgramFieldType.EMAIL, true, List.of()),
                        new ProgramField("interests", "Interests", ProgramFieldType.MULTISELECT, false,
                                List.of("Tech", "Health", "Education")))),
                new ProgramStep("review", "Review & Submit", List.of()));
        return Program.builder().id("p-" + slug).slug(slug).name(slug).tagline("Tagline").description("Description")
                .status(status).applicationStepsJson(codec.writeApplicationSteps(steps)).build();
    }

    @BeforeEach
    void stubProgramLookups() {
        lenient().when(programRepository.findBySlugIgnoreCase("spark")).thenReturn(Optional.of(testProgram("SPARK", ProgramStatus.PUBLISHED)));
        lenient().when(programRepository.findBySlugIgnoreCase("ignite")).thenReturn(Optional.of(testProgram("IGNITE", ProgramStatus.PUBLISHED)));
    }

    private ProgramApplication draft(String userId, String program) {
        return ProgramApplication.builder().id("a1").applicantUserId(userId).program(program)
                .status(ProgramApplicationStatus.DRAFT).answers(new java.util.HashMap<>()).build();
    }

    @Test
    void savingADraftForTheFirstTimeCreatesOne() {
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "SPARK"))
                .thenReturn(Optional.empty());
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ProgramApplicationDto dto = service().saveDraft("user1", "spark", Map.of("fullName", "Asha Rao"));

        assertThat(dto.status()).isEqualTo("DRAFT");
        assertThat(dto.answers()).containsEntry("fullName", "Asha Rao");
        ArgumentCaptor<ProgramApplication> captor = ArgumentCaptor.forClass(ProgramApplication.class);
        verify(programApplicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getApplicantUserId()).isEqualTo("user1");
        assertThat(captor.getValue().getProgram()).isEqualTo("SPARK");
    }

    @Test
    void savingADraftTwiceMergesAnswersRatherThanReplacingThem() {
        ProgramApplication existing = draft("user1", "SPARK");
        existing.getAnswers().put("fullName", "Asha Rao");
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "SPARK"))
                .thenReturn(Optional.of(existing));
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ProgramApplicationDto dto = service().saveDraft("user1", "spark", Map.of("email", "asha@example.com"));

        assertThat(dto.answers()).containsEntry("fullName", "Asha Rao").containsEntry("email", "asha@example.com");
    }

    @Test
    void savingADraftRejectsAnUnknownFieldKey() {
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "SPARK"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().saveDraft("user1", "spark", Map.of("notARealField", "x")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void savingADraftRejectsAMalformedEmail() {
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "SPARK"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().saveDraft("user1", "spark", Map.of("email", "not-an-email")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void savingADraftRejectsAMultiselectValueOutsideItsOptions() {
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "SPARK"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().saveDraft("user1", "spark", Map.of("interests", "Tech|NotARealOption")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void savingADraftForAProgramStillInDraftStatusIsRejectedAsUnknown() {
        when(programRepository.findBySlugIgnoreCase("test-program"))
                .thenReturn(Optional.of(testProgram("TEST-PROGRAM", ProgramStatus.DRAFT)));

        assertThatThrownBy(() -> service().saveDraft("user1", "test-program", Map.of("fullName", "Asha Rao")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cannotStartASecondDraftWhileOneIsAlreadySubmitted() {
        ProgramApplication submitted = draft("user1", "SPARK");
        submitted.setStatus(ProgramApplicationStatus.SUBMITTED);
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "SPARK"))
                .thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> service().saveDraft("user1", "spark", Map.of("fullName", "Asha Rao")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void canStartAFreshDraftAfterAPreviousRejection() {
        ProgramApplication rejected = draft("user1", "SPARK");
        rejected.setStatus(ProgramApplicationStatus.REJECTED);
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "SPARK"))
                .thenReturn(Optional.of(rejected));
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ProgramApplicationDto dto = service().saveDraft("user1", "spark", Map.of("fullName", "Asha Rao"));

        assertThat(dto.status()).isEqualTo("DRAFT");
    }

    @Test
    void submittingWithMissingRequiredFieldsIsRejectedAndListsWhatIsMissing() {
        ProgramApplication application = draft("user1", "SPARK");
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "SPARK"))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service().submit("user1", "spark"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Full Name");
    }

    @Test
    void submittingWithEveryRequiredFieldFilledSucceeds() {
        ProgramApplication application = draft("user1", "SPARK");
        application.getAnswers().putAll(Map.of("fullName", "Asha Rao", "email", "asha@example.com"));
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "SPARK"))
                .thenReturn(Optional.of(application));
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ProgramApplicationDto dto = service().submit("user1", "spark");

        assertThat(dto.status()).isEqualTo("SUBMITTED");
        assertThat(dto.submittedAt()).isNotNull();
    }

    @Test
    void cannotSubmitTwice() {
        ProgramApplication submitted = draft("user1", "SPARK");
        submitted.setStatus(ProgramApplicationStatus.SUBMITTED);
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "SPARK"))
                .thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> service().submit("user1", "spark")).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotSubmitWithoutEverStartingADraft() {
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "SPARK"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().submit("user1", "spark")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aUserCannotWithdrawSomeoneElsesApplication() {
        ProgramApplication other = draft("user2", "SPARK");
        other.setStatus(ProgramApplicationStatus.SUBMITTED);
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service().withdraw("user1", "a1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void withdrawingYourOwnSubmittedApplicationSetsItToWithdrawn() {
        ProgramApplication mine = draft("user1", "SPARK");
        mine.setStatus(ProgramApplicationStatus.SUBMITTED);
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(mine));
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ProgramApplicationDto dto = service().withdraw("user1", "a1");

        assertThat(dto.status()).isEqualTo("WITHDRAWN");
    }

    @Test
    void cannotWithdrawAnAlreadySelectedApplication() {
        ProgramApplication selected = draft("user1", "SPARK");
        selected.setStatus(ProgramApplicationStatus.SELECTED);
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(selected));

        assertThatThrownBy(() -> service().withdraw("user1", "a1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void gettingYourApplicationForAProgramYouNeverStartedIsNotFound() {
        when(programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc("user1", "IGNITE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMineForProgram("user1", "ignite")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void myApplicationsShowsTheLatestRowPerProgramOnly() {
        ProgramApplication oldSpark = draft("user1", "SPARK");
        oldSpark.setId("old");
        oldSpark.setStatus(ProgramApplicationStatus.REJECTED);
        ProgramApplication newSpark = draft("user1", "SPARK");
        newSpark.setId("new");
        ProgramApplication ignite = draft("user1", "IGNITE");
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
        when(programRepository.findBySlugIgnoreCase("not-a-program")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMineForProgram("user1", "not-a-program"))
                .isInstanceOf(BadRequestException.class);
    }
}
