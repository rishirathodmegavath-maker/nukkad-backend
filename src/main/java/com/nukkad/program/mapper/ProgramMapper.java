package com.nukkad.program.mapper;

import com.nukkad.program.catalog.ProgramContent;
import com.nukkad.program.dto.ProgramApplicationDto;
import com.nukkad.program.dto.ProgramDto;
import com.nukkad.program.entity.ProgramApplication;
import com.nukkad.program.entity.ProgramSettings;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class ProgramMapper {

    public ProgramDto toDto(ProgramContent content, ProgramSettings settings) {
        return new ProgramDto(
                content.program().name(),
                content.name(),
                content.tagline(),
                content.description(),
                content.highlights(),
                content.targetAudience(),
                content.journey(),
                content.benefits(),
                content.outcome(),
                content.applicationSteps(),
                settings == null || settings.isApplicationOpen(),
                settings == null ? null : settings.getFeeAmount(),
                settings == null ? null : settings.getFeeCurrency(),
                settings == null ? null : settings.getEnrollmentInfo(),
                settings == null ? null : settings.getSelective());
    }

    public ProgramApplicationDto toDto(ProgramApplication application) {
        return new ProgramApplicationDto(
                application.getId(),
                application.getProgram().name(),
                application.getStatus().name(),
                // Copied, not passed through: answers is a lazy @ElementCollection and this DTO is
                // serialized after the transaction's Hibernate session has closed (see ChapterMapper's
                // identical fix for focusAreas, which crashed production the same way).
                new HashMap<>(application.getAnswers()),
                application.getSubmittedAt(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
