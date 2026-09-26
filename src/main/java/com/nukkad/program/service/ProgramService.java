package com.nukkad.program.service;

import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.program.dto.ProgramDto;
import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramStatus;
import com.nukkad.program.mapper.ProgramMapper;
import com.nukkad.program.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Program discovery — the public landing page's card list and each program's detail page. Only
 *  PUBLISHED programs are ever listed; a direct link to an ARCHIVED one still resolves (see {@link
 *  ProgramStatus}'s doc comment), but DRAFT never does, to anyone who isn't Admin. */
@Service
public class ProgramService {

    private final ProgramRepository programRepository;
    private final ProgramMapper programMapper;

    public ProgramService(ProgramRepository programRepository, ProgramMapper programMapper) {
        this.programRepository = programRepository;
        this.programMapper = programMapper;
    }

    @Transactional(readOnly = true)
    public List<ProgramDto> list() {
        return programRepository.findByStatusOrderByDisplayOrderAscNameAsc(ProgramStatus.PUBLISHED).stream()
                .map(programMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramDto get(String slug) {
        Program program = getPublicEntityOrThrow(slug);
        return programMapper.toDto(program);
    }

    /** PUBLISHED or ARCHIVED — never DRAFT, which is admin-only (see class doc). */
    @Transactional(readOnly = true)
    public Program getPublicEntityOrThrow(String slug) {
        Program program = programRepository.findBySlugIgnoreCase(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown program: " + slug));
        if (program.getStatus() == ProgramStatus.DRAFT) {
            throw new ResourceNotFoundException("Unknown program: " + slug);
        }
        return program;
    }

    /** For the application workflow: a program an applicant may apply to must exist and not be a
     *  draft — but whether it's currently PUBLISHED vs ARCHIVED doesn't gate starting/resuming a
     *  draft or reading back an already-submitted application (an archived program's past
     *  applicants must still be able to see their own history). */
    @Transactional(readOnly = true)
    public Program getApplicableEntityOrThrow(String slug) {
        return getPublicEntityOrThrow(slug);
    }
}
