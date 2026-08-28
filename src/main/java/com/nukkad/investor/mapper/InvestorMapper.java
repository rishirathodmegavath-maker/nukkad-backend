package com.nukkad.investor.mapper;

import com.nukkad.investor.dto.FundraiseDto;
import com.nukkad.investor.dto.IntroRequestDto;
import com.nukkad.investor.dto.InvestorProfileDto;
import com.nukkad.investor.entity.Fundraise;
import com.nukkad.investor.entity.IntroRequest;
import com.nukkad.investor.entity.InvestorProfile;
import com.nukkad.user.dto.UserDto;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class InvestorMapper {

    public InvestorProfileDto toDto(InvestorProfile profile, UserDto user, boolean canManage) {
        return new InvestorProfileDto(
                profile.getId(),
                profile.getUserId(),
                user,
                profile.getInvestorType().getLabel(),
                profile.getFirmName(),
                profile.getThesis(),
                new HashSet<>(profile.getSectors()),
                new HashSet<>(profile.getStages()),
                new HashSet<>(profile.getGeographies()),
                profile.getTicketMin(),
                profile.getTicketMax(),
                profile.getPortfolioCount(),
                profile.getWebsite(),
                canManage,
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    public FundraiseDto toDto(Fundraise fundraise, String startupName, boolean canManage) {
        return new FundraiseDto(
                fundraise.getId(),
                fundraise.getStartupId(),
                startupName,
                fundraise.getTargetAmount(),
                fundraise.getAmountRaised(),
                fundraise.getFundingStage().getLabel(),
                fundraise.getUseOfFunds(),
                fundraise.getMinimumTicket(),
                fundraise.getStatus().getLabel(),
                canManage,
                fundraise.getCreatedAt(),
                fundraise.getUpdatedAt()
        );
    }

    public IntroRequestDto toDto(IntroRequest request, UserDto requester, UserDto recipient, String startupName, String ideaTitle) {
        return new IntroRequestDto(
                request.getId(),
                request.getRequesterId(),
                requester,
                request.getRecipientId(),
                recipient,
                request.getDirection().name(),
                request.getStartupId(),
                startupName,
                request.getIdeaId(),
                ideaTitle,
                request.getMessage(),
                request.getStatus().getLabel(),
                request.getCreatedAt(),
                request.getReviewedAt()
        );
    }
}
