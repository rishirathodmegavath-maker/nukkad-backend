package com.nukkad.opportunity.mapper;

import com.nukkad.opportunity.dto.OpportunityDto;
import com.nukkad.opportunity.entity.Opportunity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;

@Component
public class OpportunityMapper {

    public OpportunityDto toDto(Opportunity opportunity) {
        return toDto(opportunity, false, false, null, 0, 0, null);
    }

    public OpportunityDto toDto(Opportunity opportunity, boolean hasApplied, boolean hasExpressedInterest,
                                 String applicationStatus, int applicantCount, int interestCount,
                                 Instant appliedAt) {
        return new OpportunityDto(
                opportunity.getId(),
                opportunity.getTitle(),
                opportunity.getType().getLabel(),
                opportunity.isClosed(),
                opportunity.isRemovedByAdmin(),
                opportunity.getRemovalReason(),
                opportunity.getModerationStatus().name(),
                opportunity.getRejectionReason(),
                opportunity.getStartupId(),
                opportunity.getOrganizationName(),
                opportunity.getLocation(),
                opportunity.getWorkMode().getLabel(),
                opportunity.getDescription(),
                opportunity.getResponsibilities(),
                opportunity.getCompensation(),
                opportunity.getEquity(),
                opportunity.getExperienceLevel(),
                opportunity.getApplicationDeadline(),
                opportunity.getPostedByUserId(),
                opportunity.isPostedAsPlatform(),
                opportunity.getPublisherIdentity().name(),
                opportunity.getChapterId(),
                new ArrayList<>(opportunity.getRequirements()),
                new ArrayList<>(opportunity.getRequiredSkills()),
                hasApplied,
                hasExpressedInterest,
                applicationStatus,
                applicantCount,
                interestCount,
                appliedAt,
                opportunity.getCreatedAt(),
                opportunity.getUpdatedAt()
        );
    }
}
