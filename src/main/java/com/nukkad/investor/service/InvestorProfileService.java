package com.nukkad.investor.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.investor.dto.CreateInvestorProfileRequest;
import com.nukkad.investor.dto.InvestorProfileDto;
import com.nukkad.investor.dto.UpdateInvestorProfileRequest;
import com.nukkad.investor.entity.InvestorProfile;
import com.nukkad.investor.entity.InvestorType;
import com.nukkad.investor.mapper.InvestorMapper;
import com.nukkad.investor.repository.InvestorProfileRepository;
import com.nukkad.investor.repository.InvestorProfileSpecifications;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
public class InvestorProfileService {

    private final InvestorProfileRepository investorProfileRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final InvestorMapper investorMapper;

    public InvestorProfileService(InvestorProfileRepository investorProfileRepository,
                                   UserRepository userRepository,
                                   UserService userService,
                                   InvestorMapper investorMapper) {
        this.investorProfileRepository = investorProfileRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.investorMapper = investorMapper;
    }

    public InvestorProfile getEntityOrThrow(String id) {
        return investorProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Investor profile not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<InvestorProfileDto> list(String type, String sector, String stage, String geography, Long ticketSize,
                                          String q, String viewerId, int page, int size) {
        Specification<InvestorProfile> spec = InvestorProfileSpecifications.combine(
                InvestorProfileSpecifications.search(q),
                InvestorProfileSpecifications.type(type),
                InvestorProfileSpecifications.sector(sector),
                InvestorProfileSpecifications.stage(stage),
                InvestorProfileSpecifications.geography(geography),
                InvestorProfileSpecifications.ticketSize(ticketSize)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return investorProfileRepository.findAll(spec, pageable).map(p -> toDto(p, viewerId));
    }

    @Transactional(readOnly = true)
    public InvestorProfileDto get(String id, String viewerId) {
        return toDto(getEntityOrThrow(id), viewerId);
    }

    @Transactional(readOnly = true)
    public InvestorProfileDto getMine(String userId) {
        InvestorProfile profile = investorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("You don't have an investor profile yet"));
        return toDto(profile, userId);
    }

    @Transactional(readOnly = true)
    public InvestorProfileDto getByUserId(String userId, String viewerId) {
        InvestorProfile profile = investorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("This user does not have an investor profile"));
        return toDto(profile, viewerId);
    }

    @Transactional
    public InvestorProfileDto create(String userId, CreateInvestorProfileRequest request) {
        if (investorProfileRepository.existsByUserId(userId)) {
            throw new ConflictException("You already have an investor profile");
        }
        InvestorType type = parseType(request.investorType());

        InvestorProfile profile = InvestorProfile.builder()
                .userId(userId)
                .investorType(type)
                .firmName(request.firmName())
                .thesis(request.thesis())
                .sectors(request.sectors() == null ? new HashSet<>() : new HashSet<>(request.sectors()))
                .stages(request.stages() == null ? new HashSet<>() : new HashSet<>(request.stages()))
                .geographies(request.geographies() == null ? new HashSet<>() : new HashSet<>(request.geographies()))
                .ticketMin(request.ticketMin())
                .ticketMax(request.ticketMax())
                .portfolioCount(request.portfolioCount() == null ? 0 : request.portfolioCount())
                .website(request.website())
                .build();
        profile = investorProfileRepository.saveAndFlush(profile);

        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.getSecurityRoles().add(SecurityRole.INVESTOR);
        userRepository.save(user);

        return toDto(profile, userId);
    }

    @Transactional
    public InvestorProfileDto update(String userId, String id, UpdateInvestorProfileRequest request) {
        InvestorProfile profile = getEntityOrThrow(id);
        requireOwner(userId, profile);

        if (request.investorType() != null) profile.setInvestorType(parseType(request.investorType()));
        if (request.firmName() != null) profile.setFirmName(request.firmName());
        if (request.thesis() != null) profile.setThesis(request.thesis());
        if (request.sectors() != null) profile.setSectors(new HashSet<>(request.sectors()));
        if (request.stages() != null) profile.setStages(new HashSet<>(request.stages()));
        if (request.geographies() != null) profile.setGeographies(new HashSet<>(request.geographies()));
        if (request.ticketMin() != null) profile.setTicketMin(request.ticketMin());
        if (request.ticketMax() != null) profile.setTicketMax(request.ticketMax());
        if (request.portfolioCount() != null) profile.setPortfolioCount(request.portfolioCount());
        if (request.website() != null) profile.setWebsite(request.website());

        return toDto(investorProfileRepository.saveAndFlush(profile), userId);
    }

    @Transactional
    public void delete(String userId, String id) {
        InvestorProfile profile = getEntityOrThrow(id);
        requireOwner(userId, profile);
        investorProfileRepository.delete(profile);

        userRepository.findById(userId).ifPresent(user -> {
            user.getSecurityRoles().remove(SecurityRole.INVESTOR);
            userRepository.save(user);
        });
    }

    private InvestorType parseType(String label) {
        try {
            return InvestorType.fromLabel(label);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown investor type: " + label);
        }
    }

    private void requireOwner(String userId, InvestorProfile profile) {
        if (!userId.equals(profile.getUserId())) {
            throw new ForbiddenException("Only the owner of this investor profile can perform this action");
        }
    }

    private InvestorProfileDto toDto(InvestorProfile profile, String viewerId) {
        UserDto user = userService.getUser(profile.getUserId(), viewerId);
        boolean canManage = viewerId != null && viewerId.equals(profile.getUserId());
        return investorMapper.toDto(profile, user, canManage);
    }
}
