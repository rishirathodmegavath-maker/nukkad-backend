package com.nukkad.admin.mapper;

import com.nukkad.admin.dto.AdminProgramDto;
import com.nukkad.program.catalog.ProgramContentCodec;
import com.nukkad.program.entity.Program;
import org.springframework.stereotype.Component;

@Component
public class AdminProgramMapper {

    private final ProgramContentCodec codec;

    public AdminProgramMapper(ProgramContentCodec codec) {
        this.codec = codec;
    }

    public AdminProgramDto toDto(Program program, long applicationCount) {
        return new AdminProgramDto(
                program.getId(),
                program.getSlug(),
                program.getName(),
                program.getBadge(),
                program.getTagline(),
                program.getDescription(),
                program.getHeroImageUrl(),
                program.getThumbnailUrl(),
                program.getStatus().name(),
                program.getDisplayOrder(),
                codec.readStrings(program.getHighlightsJson()),
                codec.readStrings(program.getTargetAudienceJson()),
                program.getAudienceDescription(),
                program.getEligibilityTitle(),
                program.getEligibilityDescription(),
                codec.readStrings(program.getEligibilityPointsJson()),
                codec.readJourney(program.getJourneyJson()),
                codec.readBenefits(program.getBenefitsJson()),
                program.getOutcomeHeading(),
                program.getOutcomeDescription(),
                codec.readApplicationSteps(program.getApplicationStepsJson()),
                program.isApplicationOpen(),
                program.getFeeAmount(),
                program.getFeeCurrency(),
                program.getEnrollmentInfo(),
                program.getSelective(),
                applicationCount,
                program.getCreatedAt(),
                program.getUpdatedAt());
    }
}
