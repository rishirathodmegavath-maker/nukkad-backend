package com.nukkad.admin.service;

import com.nukkad.admin.dto.UpdateProgramSettingsRequest;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.program.dto.ProgramDto;
import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramSettings;
import com.nukkad.program.mapper.ProgramMapper;
import com.nukkad.program.repository.ProgramSettingsRepository;
import com.nukkad.program.service.ProgramService;
import com.nukkad.program.catalog.ProgramCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** The only part of a program Admin can edit — see {@link ProgramCatalog}'s doc comment for why
 *  its content (copy, journey, benefits) is fixed code rather than admin-editable. */
@Service
public class AdminProgramSettingsService {

    private final ProgramSettingsRepository programSettingsRepository;
    private final ProgramMapper programMapper;
    private final AuditService auditService;

    public AdminProgramSettingsService(ProgramSettingsRepository programSettingsRepository, ProgramMapper programMapper,
                                        AuditService auditService) {
        this.programSettingsRepository = programSettingsRepository;
        this.programMapper = programMapper;
        this.auditService = auditService;
    }

    @Transactional
    public ProgramDto update(String adminId, String programKey, UpdateProgramSettingsRequest request, String ip) {
        Program program = ProgramService.parseProgram(programKey);
        ProgramSettings settings = programSettingsRepository.findById(program)
                .orElseGet(() -> ProgramSettings.builder().program(program).applicationOpen(true).build());

        if (request.applicationOpen() != null) settings.setApplicationOpen(request.applicationOpen());
        if (request.feeAmount() != null) settings.setFeeAmount(request.feeAmount());
        if (request.feeCurrency() != null) settings.setFeeCurrency(request.feeCurrency());
        if (request.enrollmentInfo() != null) settings.setEnrollmentInfo(request.enrollmentInfo());
        if (request.selective() != null) settings.setSelective(request.selective());

        settings = programSettingsRepository.saveAndFlush(settings);
        auditService.log(adminId, AuditAction.ADMIN_ACTION, "ProgramSettings", program.name(), ip,
                Map.of("action", "ADMIN_PROGRAM_SETTINGS_UPDATED"));

        return programMapper.toDto(ProgramCatalog.get(program), settings);
    }
}
