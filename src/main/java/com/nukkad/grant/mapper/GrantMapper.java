package com.nukkad.grant.mapper;

import com.nukkad.grant.dto.GrantDto;
import com.nukkad.grant.entity.Grant;
import com.nukkad.grant.entity.GrantDiscoveryOrigin;
import com.nukkad.startup.entity.StartupStage;
import org.springframework.stereotype.Component;

@Component
public class GrantMapper {

    public GrantDto toDto(Grant grant, boolean canManage) {
        return new GrantDto(
                grant.getId(),
                grant.getName(),
                grant.getProvider(),
                grant.getProviderType().getLabel(),
                grant.getDescription(),
                grant.getFundingAmount(),
                grant.getEligibilityCriteria(),
                grant.getEligibleSectors().stream().sorted().toList(),
                grant.getEligibleStages().stream().map(StartupStage::getLabel).sorted().toList(),
                grant.getDeadline(),
                grant.getApplicationUrl(),
                grant.getSourceUrl(),
                grant.getDiscoveryOrigin() == GrantDiscoveryOrigin.AI_DISCOVERY ? "AI Discovery" : "Manual",
                grant.getLastVerifiedAt(),
                grant.getCreatedByUserId(),
                grant.isPostedAsPlatform(),
                grant.getPublisherIdentity().name(),
                grant.isRemovedByAdmin(),
                grant.getRemovalReason(),
                grant.getModerationStatus().name(),
                grant.getRejectionReason(),
                canManage,
                grant.getCreatedAt(),
                grant.getUpdatedAt()
        );
    }
}
