package com.nukkad.investor.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.moderation.ModerationStatus;
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
                FundraiseSpecifications.stage(stage),
                FundraiseSpecifications.visibleTo(viewerId)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return fundraiseRepository.findAll(spec, pageable).map(f -> toDto(f, viewerId));
    }

    /** Direct fetch by fundraise id must not bypass fundraising-visibility — treated as "not
     *  found" for a viewer who isn't allowed to see it, same as StartupService does for a
     *  member-only startup viewed anonymously. */
    @Transactional(readOnly = true)
    public FundraiseDto get(String id, String viewerId) {
        Fundraise fundraise = getEntityOrThrow(id);
        if (!canView(fundraise, viewerId)) {
            throw new ResourceNotFoundException("Fundraise not found: " + id);
        }
        return toDto(fundraise, viewerId);
    }

    @Transactional(readOnly = true)
    public FundraiseDto getByStartup(String startupId, String viewerId) {
        return fundraiseRepository.findByStartupId(startupId)
                .filter(f -> canView(f, viewerId))
                .map(f -> toDto(f, viewerId))
                .orElse(null);
    }

    /** A fundraise is never visible to a non-team viewer while its parent startup is still
     *  PENDING/REJECTED — an unapproved startup soliciting real money would otherwise be
     *  discoverable to the whole platform the instant it's created. Team members can still see
     *  their own fundraise while the startup awaits review, same as every other own-content
     *  bypass this session added for Ideas/Startups/Opportunities. */
    private boolean canView(Fundraise fundraise, String viewerId) {
        Startup startup = startupRepository.findById(fundraise.getStartupId()).orElse(null);
        if (startup == null) return false;
        boolean isTeamMember = viewerId != null && teamMemberRepository.findByStartupIdAndUserId(startup.getId(), viewerId)
                .map(m -> m.getStatus() == StartupTeamMember.Status.ACTIVE)
                .orElse(false);
        if (isTeamMember) return true;
        if (startup.getModerationStatus() != ModerationStatus.APPROVED) return false;
        return startup.isFundraisingVisible();
    }

    @Transactional
    public FundraiseDto create(String userId, CreateFundraiseRequest request) {
        Startup startup = startupRepository.findById(request.startupId())
                .orElseThrow(() -> new ResourceNotFoundException("Startup not found: " + request.startupId()));
        requireManager(userId, startup.getId());
        if (startup.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new BadRequestException("This startup must be approved by an admin before it can raise funds");
        }
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
        requireManager(userId, fundraise.getStartupId());
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
        requireManager(userId, startupId);
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

    private void requireManager(String userId, String startupId) {
        boolean canManage = teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .map(StartupTeamMember::canManage)
                .orElse(false);
        if (!canManage) throw new ForbiddenException("Only a founder or admin of this startup can perform this action");
    }

    private FundraiseDto toDto(Fundraise fundraise, String viewerId) {
        String startupName = startupRepository.findById(fundraise.getStartupId()).map(Startup::getName).orElse(null);
        boolean canManage = viewerId != null && teamMemberRepository.findByStartupIdAndUserId(fundraise.getStartupId(), viewerId)
                .map(StartupTeamMember::canManage)
                .orElse(false);
        return investorMapper.toDto(fundraise, startupName, canManage);
    }
}
