package com.nukkad.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nukkad.admin.dto.AdminProgramDto;
import com.nukkad.admin.dto.CreateProgramRequest;
import com.nukkad.admin.dto.UpdateProgramRequest;
import com.nukkad.admin.mapper.AdminProgramMapper;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.program.catalog.ProgramBenefit;
import com.nukkad.program.catalog.ProgramContentCodec;
import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramApplicationStatus;
import com.nukkad.program.entity.ProgramStatus;
import com.nukkad.program.repository.ProgramApplicationRepository;
import com.nukkad.program.repository.ProgramRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProgramServiceTest {

    @Mock private ProgramRepository programRepository;
    @Mock private ProgramApplicationRepository programApplicationRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditService auditService;
    private final ProgramContentCodec codec = new ProgramContentCodec(new ObjectMapper());
    private final AdminProgramMapper adminProgramMapper = new AdminProgramMapper(codec);

    private AdminProgramService service() {
        return new AdminProgramService(programRepository, programApplicationRepository, adminProgramMapper, codec, fileStorageService, auditService);
    }

    private CreateProgramRequest minimalCreateRequest(String slug) {
        return CreateProgramRequest.builder().slug(slug).name("Test Program")
                .tagline("A short description.").description("A longer description.").status("DRAFT").build();
    }

    @Test
    void adminCanCreateAProgramAsDraft() {
        when(programRepository.existsBySlugIgnoreCase("test-program")).thenReturn(false);
        when(programRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Program p = inv.getArgument(0);
            p.setId("prog1");
            return p;
        });

        AdminProgramDto dto = service().create("admin1", minimalCreateRequest("test-program"), "127.0.0.1");

        assertThat(dto.status()).isEqualTo("DRAFT");
        assertThat(dto.slug()).isEqualTo("test-program");
        assertThat(dto.name()).isEqualTo("Test Program");
        verify(auditService).log(eq("admin1"), any(), eq("Program"), eq("prog1"), any(), any());
    }

    @Test
    void creatingAProgramWithADuplicateSlugIsRejected() {
        when(programRepository.existsBySlugIgnoreCase("spark")).thenReturn(true);

        assertThatThrownBy(() -> service().create("admin1", minimalCreateRequest("spark"), "127.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void creatingAProgramWithAnInvalidSlugIsRejected() {
        assertThatThrownBy(() -> service().create("admin1", minimalCreateRequest("has a space"), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void draftProgramsAreNeverPublic() {
        // ProgramService (the public side) is what a member calls; this just confirms Admin's own
        // create defaults to DRAFT unless a status is explicitly given, so nothing new goes public
        // by accident. See ProgramServiceTest for the public-visibility filter itself.
        CreateProgramRequest request = CreateProgramRequest.builder().slug("draft-one").name("Draft One")
                .tagline("Short").description("Long").build();
        when(programRepository.existsBySlugIgnoreCase("draft-one")).thenReturn(false);
        when(programRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminProgramDto dto = service().create("admin1", request, "127.0.0.1");

        assertThat(dto.status()).isEqualTo("DRAFT");
    }

    @Test
    void publishingAProgramUpdatesItsStatus() {
        Program program = Program.builder().id("prog1").slug("test-program").name("Test Program")
                .tagline("t").description("d").status(ProgramStatus.DRAFT).build();
        when(programRepository.findById("prog1")).thenReturn(Optional.of(program));
        when(programRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(programApplicationRepository.countByProgramAndStatusNot("test-program", ProgramApplicationStatus.DRAFT)).thenReturn(0L);

        UpdateProgramRequest request = UpdateProgramRequest.builder().status("PUBLISHED").build();
        AdminProgramDto dto = service().update("admin1", "prog1", request, "127.0.0.1");

        assertThat(dto.status()).isEqualTo("PUBLISHED");
        ArgumentCaptor<java.util.Map<String, Object>> details = ArgumentCaptor.forClass(java.util.Map.class);
        verify(auditService).log(eq("admin1"), any(), eq("Program"), eq("prog1"), any(), details.capture());
        assertThat(details.getValue()).containsEntry("action", "PROGRAM_PUBLISHED");
    }

    @Test
    void updatingContentFieldsPersistsAudienceEligibilityAndBenefitsWithRecomputedOrder() {
        Program program = Program.builder().id("prog1").slug("test-program").name("Test Program")
                .tagline("t").description("d").status(ProgramStatus.DRAFT).build();
        when(programRepository.findById("prog1")).thenReturn(Optional.of(program));
        when(programRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(programApplicationRepository.countByProgramAndStatusNot(anyString(), any())).thenReturn(0L);

        UpdateProgramRequest request = UpdateProgramRequest.builder()
                .targetAudience(List.of("Early-stage founders"))
                .audienceDescription("For people building something real.")
                .eligibilityTitle("Eligibility")
                .eligibilityDescription("Who can apply")
                .eligibilityPoints(List.of("Has an idea", "Is a student"))
                // benefits sent out of order on purpose: order must be recomputed from position, not trusted.
                .benefits(List.of(new ProgramBenefit(99, "rocket", "Live sessions", null), new ProgramBenefit(1, "users", "Community", "Peer support")))
                .build();
        AdminProgramDto dto = service().update("admin1", "prog1", request, "127.0.0.1");

        assertThat(dto.targetAudience()).containsExactly("Early-stage founders");
        assertThat(dto.audienceDescription()).isEqualTo("For people building something real.");
        assertThat(dto.eligibilityTitle()).isEqualTo("Eligibility");
        assertThat(dto.eligibilityPoints()).containsExactly("Has an idea", "Is a student");
        assertThat(dto.benefits()).extracting(ProgramBenefit::order).containsExactly(0, 1);
        assertThat(dto.benefits()).extracting(ProgramBenefit::title).containsExactly("Live sessions", "Community");
    }

    @Test
    void renamingASlugCascadesToExistingApplications() {
        Program program = Program.builder().id("prog1").slug("old-slug").name("Test Program")
                .tagline("t").description("d").status(ProgramStatus.PUBLISHED).build();
        when(programRepository.findById("prog1")).thenReturn(Optional.of(program));
        when(programRepository.existsBySlugIgnoreCase("new-slug")).thenReturn(false);
        when(programRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(programApplicationRepository.countByProgramAndStatusNot(anyString(), any())).thenReturn(0L);

        UpdateProgramRequest request = UpdateProgramRequest.builder().slug("new-slug").build();
        service().update("admin1", "prog1", request, "127.0.0.1");

        verify(programApplicationRepository).renameProgramSlug("old-slug", "new-slug");
    }

    @Test
    void renamingToASlugAlreadyInUseIsRejected() {
        Program program = Program.builder().id("prog1").slug("old-slug").name("Test Program")
                .tagline("t").description("d").status(ProgramStatus.PUBLISHED).build();
        when(programRepository.findById("prog1")).thenReturn(Optional.of(program));
        when(programRepository.existsBySlugIgnoreCase("spark")).thenReturn(true);

        UpdateProgramRequest request = UpdateProgramRequest.builder().slug("spark").build();

        assertThatThrownBy(() -> service().update("admin1", "prog1", request, "127.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deletingAProgramWithApplicationsOnRecordIsRejected() {
        Program program = Program.builder().id("prog1").slug("test-program").name("Test Program")
                .tagline("t").description("d").status(ProgramStatus.PUBLISHED).build();
        when(programRepository.findById("prog1")).thenReturn(Optional.of(program));
        when(programApplicationRepository.count(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<com.nukkad.program.entity.ProgramApplication>>any()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service().delete("admin1", "prog1", "127.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deletingAProgramWithNoApplicationsSucceedsAndCleansUpItsImages() {
        Program program = Program.builder().id("prog1").slug("test-program").name("Test Program")
                .tagline("t").description("d").status(ProgramStatus.DRAFT)
                .heroImageUrl("https://cdn.example/hero.png").thumbnailUrl("https://cdn.example/thumb.png").build();
        when(programRepository.findById("prog1")).thenReturn(Optional.of(program));
        when(programApplicationRepository.count(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<com.nukkad.program.entity.ProgramApplication>>any()))
                .thenReturn(0L);

        service().delete("admin1", "prog1", "127.0.0.1");

        verify(programRepository).delete(program);
        verify(fileStorageService).deleteIfHosted("https://cdn.example/hero.png");
        verify(fileStorageService).deleteIfHosted("https://cdn.example/thumb.png");
    }

    @Test
    void anOversizedHeroImageIsRejectedBeforeStorageIsEverCalled() {
        MockMultipartFile oversized = new MockMultipartFile("file", "hero.png", "image/png", new byte[1]) {
            @Override
            public long getSize() {
                return 9L * 1024 * 1024;
            }
        };

        assertThatThrownBy(() -> service().uploadHeroImage(oversized)).isInstanceOf(BadRequestException.class);
        verify(fileStorageService, org.mockito.Mockito.never()).storeImage(any(), any());
    }
}
