package com.nukkad.program.service;

import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.program.catalog.ProgramContentCodec;
import com.nukkad.program.dto.ProgramDto;
import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramStatus;
import com.nukkad.program.mapper.ProgramMapper;
import com.nukkad.program.repository.ProgramRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** The public half's visibility rules: DRAFT is admin-only (never listed, never resolvable by a
 *  public caller), PUBLISHED is fully public, ARCHIVED is de-listed but still resolvable by direct
 *  link (see ProgramStatus's own doc comment on why). */
@ExtendWith(MockitoExtension.class)
class ProgramServiceTest {

    @Mock private ProgramRepository programRepository;
    private final ProgramContentCodec codec = new ProgramContentCodec(new ObjectMapper());
    private final ProgramMapper programMapper = new ProgramMapper(codec);

    private ProgramService service() {
        return new ProgramService(programRepository, programMapper);
    }

    private Program program(String slug, ProgramStatus status) {
        return Program.builder().id("id-" + slug).slug(slug).name(slug).tagline("t").description("d").status(status).build();
    }

    @Test
    void listOnlyReturnsPublishedPrograms() {
        when(programRepository.findByStatusOrderByDisplayOrderAscNameAsc(ProgramStatus.PUBLISHED))
                .thenReturn(List.of(program("SPARK", ProgramStatus.PUBLISHED)));

        List<ProgramDto> programs = service().list();

        assertThat(programs).hasSize(1);
        assertThat(programs.get(0).key()).isEqualTo("SPARK");
    }

    @Test
    void aDraftProgramCannotBeFetchedByAPublicCaller() {
        when(programRepository.findBySlugIgnoreCase("draft-one")).thenReturn(Optional.of(program("draft-one", ProgramStatus.DRAFT)));

        assertThatThrownBy(() -> service().get("draft-one")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aPublishedProgramIsFetchable() {
        when(programRepository.findBySlugIgnoreCase("spark")).thenReturn(Optional.of(program("SPARK", ProgramStatus.PUBLISHED)));

        assertThat(service().get("spark").key()).isEqualTo("SPARK");
    }

    @Test
    void anArchivedProgramIsStillFetchableByDirectLink() {
        when(programRepository.findBySlugIgnoreCase("old-cohort")).thenReturn(Optional.of(program("old-cohort", ProgramStatus.ARCHIVED)));

        assertThat(service().get("old-cohort").key()).isEqualTo("old-cohort");
    }

    @Test
    void anUnknownSlugIsNotFound() {
        when(programRepository.findBySlugIgnoreCase("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get("nope")).isInstanceOf(ResourceNotFoundException.class);
    }
}
