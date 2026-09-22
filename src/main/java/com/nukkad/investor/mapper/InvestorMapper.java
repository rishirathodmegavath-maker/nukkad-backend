package com.nukkad.investor.mapper;

import com.nukkad.investor.dto.FundraiseDto;
import com.nukkad.investor.dto.IntroRequestDto;
import com.nukkad.investor.dto.InvestorActivationRequestDto;
import com.nukkad.investor.dto.InvestorDto;
import com.nukkad.investor.dto.InvestorIntroRequestDto;
import com.nukkad.investor.dto.InvestorProfileDto;
import com.nukkad.investor.entity.Fundraise;
import com.nukkad.investor.entity.IntroRequest;
import com.nukkad.investor.entity.Investor;
import com.nukkad.investor.entity.InvestorActivationRequest;
import com.nukkad.investor.entity.InvestorIntroRequest;
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

    public InvestorActivationRequestDto toDto(InvestorActivationRequest request) {
        return new InvestorActivationRequestDto(
                request.getId(),
                request.getStatus().name(),
                request.getInvestorType().getLabel(),
                request.getFirmName(),
                request.getThesis(),
                new HashSet<>(request.getSectors()),
                new HashSet<>(request.getStages()),
                new HashSet<>(request.getGeographies()),
                request.getTicketMin(),
                request.getTicketMax(),
                request.getPortfolioCount(),
                request.getWebsite(),
                request.getResultingProfileId(),
                request.getReviewNote(),
                request.getCreatedAt(),
                request.getReviewedAt()
        );
    }

    /** Founder-facing Investor Discovery card/profile — never carries the internal live-account link or any
     *  private contact detail (see InvestorDto). */
    public InvestorDto toDto(Investor investor) {
        return new InvestorDto(
                investor.getId(),
                investor.getName(),
                investor.getInvestorType().getLabel(),
                investor.getDescription(),
                investor.getLocation(),
                investor.getCountry(),
                investor.getWebsite(),
                investor.getDomain(),
                investor.getLogoUrl(),
                new HashSet<>(investor.getSectors()),
                new HashSet<>(investor.getStages()),
                new HashSet<>(investor.getPrograms()),
                investor.getInvestmentCount(),
                investor.getExitCount(),
                new HashSet<>(investor.getKeyPeople()),
                investor.getFacebookUrl(),
                investor.getInstagramUrl(),
                investor.getLinkedinUrl(),
                investor.getTwitterUrl(),
                investor.getChequeMin(),
                investor.getChequeMax(),
                investor.getCreatedAt()
        );
    }

    public InvestorIntroRequestDto toDto(InvestorIntroRequest request, String investorName, String requesterName, String startupName) {
        return new InvestorIntroRequestDto(
                request.getId(),
                request.getInvestorId(),
                investorName,
                request.getRequesterUserId(),
                requesterName,
                request.getStartupId(),
                startupName,
                request.getMessage(),
                request.getStatus().name(),
                request.getCreatedAt(),
                request.getClosedAt()
        );
    }

    public IntroRequestDto toDto(IntroRequest request, UserDto requester, UserDto recipient, String startupName,
                                  String ideaTitle, String conversationId) {
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
                request.getReviewedAt(),
                conversationId
        );
    }
}
