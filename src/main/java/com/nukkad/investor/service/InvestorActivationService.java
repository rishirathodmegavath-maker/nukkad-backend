package com.nukkad.investor.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.investor.dto.InvestorActivationRequestDto;
import com.nukkad.investor.dto.SubmitInvestorActivationRequest;
import com.nukkad.investor.entity.InvestorActivationRequest;
import com.nukkad.investor.entity.InvestorActivationStatus;
import com.nukkad.investor.entity.InvestorType;
import com.nukkad.investor.mapper.InvestorMapper;
import com.nukkad.investor.repository.InvestorActivationRequestRepository;
import com.nukkad.investor.repository.InvestorProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

/** The user-facing half of investor verification — submit an application, check its status.
 *  {@link com.nukkad.admin.service.AdminInvestorActivationService} is the admin-facing half that
 *  actually decides it. See the V77 migration for why this exists: an admin-reviewed gate was
 *  designed (the underlying table) but never wired to any code until now. */
@Service
public class InvestorActivationService {

    private final InvestorActivationRequestRepository activationRequestRepository;
    private final InvestorProfileRepository investorProfileRepository;
    private final InvestorMapper investorMapper;

    public InvestorActivationService(InvestorActivationRequestRepository activationRequestRepository,
                                      InvestorProfileRepository investorProfileRepository,
                                      InvestorMapper investorMapper) {
        this.activationRequestRepository = activationRequestRepository;
        this.investorProfileRepository = investorProfileRepository;
        this.investorMapper = investorMapper;
    }

    @Transactional
    public InvestorActivationRequestDto submit(String userId, SubmitInvestorActivationRequest request) {
        if (investorProfileRepository.existsByUserId(userId)) {
            throw new ConflictException("You already have an investor profile");
        }
        activationRequestRepository.findTopByRequesterUserIdOrderByCreatedAtDesc(userId).ifPresent(latest -> {
            if (latest.getStatus() == InvestorActivationStatus.PENDING) {
                throw new ConflictException("You already have a pending investor application");
            }
        });

        InvestorActivationRequest activationRequest = InvestorActivationRequest.builder()
                .requesterUserId(userId)
                .investorType(parseType(request.investorType()))
                .firmName(request.firmName())
                .thesis(request.thesis())
                .sectors(request.sectors() == null ? new HashSet<>() : new HashSet<>(request.sectors()))
                .stages(request.stages() == null ? new HashSet<>() : new HashSet<>(request.stages()))
                .geographies(request.geographies() == null ? new HashSet<>() : new HashSet<>(request.geographies()))
                .ticketMin(request.ticketMin())
                .ticketMax(request.ticketMax())
                .portfolioCount(request.portfolioCount() == null ? 0 : request.portfolioCount())
                .website(request.website())
                .status(InvestorActivationStatus.PENDING)
                .build();

        return investorMapper.toDto(activationRequestRepository.saveAndFlush(activationRequest));
    }

    @Transactional(readOnly = true)
    public InvestorActivationRequestDto getMine(String userId) {
        InvestorActivationRequest request = activationRequestRepository.findTopByRequesterUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("You haven't applied to become an investor yet"));
        return investorMapper.toDto(request);
    }

    private InvestorType parseType(String label) {
        try {
            return InvestorType.fromLabel(label);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown investor type: " + label);
        }
    }
}
