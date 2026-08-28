package com.nukkad.opportunity.mapper;

import com.nukkad.opportunity.dto.OpportunityDto;
import com.nukkad.opportunity.entity.Opportunity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class OpportunityMapper {

    public OpportunityDto toDto(Opportunity opportunity) {
        return toDto(opportunity, false, false, null, 0, 0);
    }

    public OpportunityDto toDto(Opportunity opportunity, boolean hasApplied, boolean hasExpressedInterest,
                                 String applicationStatus, int applicantCount, int interestCount) {
        return new OpportunityDto(
                opportunity.getId(),
                opportunity.getTitle(),
                opportunity.getType().getLabel(),
                opportunity.getStartupId(),
                opportunity.getOrganizationName(),
                opportunity.getLocation(),
                opportunity.isRemote(),
                opportunity.getDescription(),
                opportunity.getCompensation(),
                opportunity.getPostedByUserId(),
                opportunity.getChapterId(),
                new ArrayList<>(opportunity.getRequirements()),
                hasApplied,
                hasExpressedInterest,
                applicationStatus,
                applicantCount,
                interestCount,
                opportunity.getCreatedAt(),
                opportunity.getUpdatedAt()
        );
    }
}
