package com.nukkad.program.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.program.catalog.ProgramCatalog;
import com.nukkad.program.dto.ProgramDto;
import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramSettings;
import com.nukkad.program.mapper.ProgramMapper;
import com.nukkad.program.repository.ProgramSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProgramService {

    private final ProgramSettingsRepository programSettingsRepository;
    private final ProgramMapper programMapper;

    public ProgramService(ProgramSettingsRepository programSettingsRepository, ProgramMapper programMapper) {
        this.programSettingsRepository = programSettingsRepository;
        this.programMapper = programMapper;
    }

    @Transactional(readOnly = true)
    public List<ProgramDto> list() {
        return ProgramCatalog.all().stream()
                .map(content -> programMapper.toDto(content, programSettingsRepository.findById(content.program()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramDto get(String key) {
        Program program = parseProgram(key);
        return programMapper.toDto(ProgramCatalog.get(program), programSettingsRepository.findById(program).orElse(null));
    }

    public static Program parseProgram(String key) {
        try {
            return Program.fromSlug(key);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown program: " + key);
        }
    }
}
