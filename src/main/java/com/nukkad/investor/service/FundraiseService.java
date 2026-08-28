package com.nukkad.investor.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.investor.dto.CreateFundraiseRequest;
import com.nukkad.investor.dto.FundraiseDto;
import com.nukkad.investor.dto.UpdateFundraiseRequest;
import com.nukkad.investor.entity.Fundraise;
import com.nukkad.investor.entity.FundraiseStatus;
import com.nukkad.investor.mapper.InvestorMapper;
import com.nukkad.investor.repository.FundraiseRepository;
import com.nukkad.investor.repository.FundraiseSpecifications;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FundraiseService {

    private final FundraiseRepository fundraiseRepository;
    private final StartupRepository startupRepository;
    private final StartupTeamMemberRepository teamMemberRepository;
    private final InvestorMapper investorMapper;

    public FundraiseService(FundraiseRepository fundraiseRepository,
                             StartupRepository startupRepository,
                             StartupTeamMemberRepository teamMemberRepository,
                             InvestorMapper investorMapper) {
        this.fundraiseRepository = fundraiseRepository;
        this.startupRepository = startupRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.investorMapper = investorMapper;
    }

    public Fundraise getEntityOrThrow(String id) {
        return fundraiseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fundraise not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<FundraiseDto> list(String status, String stage, String viewerId, int page, int size) {
        Specification<Fundraise> spec = FundraiseSpecifications.combine(
                FundraiseSpecifications.status(status),
                FundraiseSpecifications.stage(stage)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return fundraiseRepository.findAll(spec, pageable).map(f -> toDto(f, viewerId));
    }

    @Transactional(readOnly = true)
    public FundraiseDto get(String id, String viewerId) {
        return toDto(getEntityOrThrow(id), viewerId);
    }

    @Transactional(readOnly = true)
    public FundraiseDto getByStartup(String startupId, String viewerId) {
        return fundraiseRepository.findByStartupId(startupId).map(f -> toDto(f, viewerId)).orElse(null);
    }

    @Transactional
    public FundraiseDto create(String userId, CreateFundraiseRequest request) {
        Startup startup = startupRepository.findById(request.startupId())
                .orElseThrow(() -> new ResourceNotFoundException("Startup not found: " + request.startupId()));
        requireFounder(userId, startup.getId());
        if (fundraiseRepository.existsByStartupId(startup.getId())) {
            throw new ConflictException("This startup already has an active fundraise");
        }
        StartupStage stage = parseStage(request.fundingStage());

        Fundraise fundraise = Fundraise.builder()
                .startupId(startup.getId())
                .targetAmount(request.targetAmount())
                .fundingStage(stage)
                .useOfFunds(request.useOfFunds())
                .minimumTicket(request.minimumTicket())
                .build();
        fundraise = fundraiseRepository.saveAndFlush(fundraise);

        startup.setRaising(true);
        startupRepository.saveAndFlush(startup);

        return toDto(fundraise, userId);
    }

    @Transactional
    public FundraiseDto update(String userId, String id, UpdateFundraiseRequest request) {
        Fundraise fundraise = getEntityOrThrow(id);
        requireFounder(userId, fundraise.getStartupId());
        if (fundraise.getStatus() == FundraiseStatus.CLOSED) {
            throw new BadRequestException("This fundraise is closed and can no longer be edited");
        }

        if (request.targetAmount() != null) fundraise.setTargetAmount(request.targetAmount());
        if (request.amountRaised() != null) fundraise.setAmountRaised(request.amountRaised());
        if (request.fundingStage() != null) fundraise.setFundingStage(parseStage(request.fundingStage()));
        if (request.useOfFunds() != null) fundraise.setUseOfFunds(request.useOfFunds());
        if (request.minimumTicket() != null) fundraise.setMinimumTicket(request.minimumTicket());

        return toDto(fundraiseRepository.saveAndFlush(fundraise), userId);
    }

    @Transactional
    public FundraiseDto close(String userId, String id) {
        Fundraise fundraise = getEntityOrThrow(id);
        String startupId = fundraise.getStartupId();
        requireFounder(userId, startupId);
        if (fundraise.getStatus() == FundraiseStatus.CLOSED) {
            throw new BadRequestException("This fundraise is already closed");
        }

        fundraise.setStatus(FundraiseStatus.CLOSED);
        fundraise = fundraiseRepository.saveAndFlush(fundraise);

        Startup startup = startupRepository.findById(startupId)
                .orElseThrow(() -> new ResourceNotFoundException("Startup not found: " + startupId));
        startup.setRaising(false);
        startupRepository.saveAndFlush(startup);

        return toDto(fundraise, userId);
    }

    private StartupStage parseStage(String label) {
        try {
            return StartupStage.fromLabel(label);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown funding stage: " + label);
        }
    }

    private void requireFounder(String userId, String startupId) {
        boolean isFounder = teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .map(StartupTeamMember::isFounder)
                .orElse(false);
        if (!isFounder) throw new ForbiddenException("Only a founder of this startup can perform this action");
    }

    private FundraiseDto toDto(Fundraise fundraise, String viewerId) {
        String startupName = startupRepository.findById(fundraise.getStartupId()).map(Startup::getName).orElse(null);
        boolean canManage = viewerId != null && teamMemberRepository.findByStartupIdAndUserId(fundraise.getStartupId(), viewerId)
                .map(StartupTeamMember::isFounder)
                .orElse(false);
        return investorMapper.toDto(fundraise, startupName, canManage);
    }
}
