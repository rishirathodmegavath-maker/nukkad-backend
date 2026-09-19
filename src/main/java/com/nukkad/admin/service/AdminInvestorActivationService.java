package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminInvestorActivationDto;
import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.investor.dto.CreateInvestorProfileRequest;
import com.nukkad.investor.entity.InvestorActivationRequest;
import com.nukkad.investor.entity.InvestorActivationStatus;
import com.nukkad.investor.repository.InvestorActivationRequestRepository;
import com.nukkad.investor.service.InvestorProfileService;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The admin-facing half of investor verification — see InvestorActivationService for the
 *  user-facing half and the V77 migration for why this whole flow exists. Approving a request
 *  reuses {@link InvestorProfileService#create} exactly — the same validated, role-granting path
 *  a self-service creation used to go through directly. */
@Service
public class AdminInvestorActivationService {

    private final InvestorActivationRequestRepository activationRequestRepository;
    private final InvestorProfileService investorProfileService;
    private final UserRepository userRepository;
    private final AdminMapper adminMapper;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public AdminInvestorActivationService(InvestorActivationRequestRepository activationRequestRepository,
                                           InvestorProfileService investorProfileService,
                                           UserRepository userRepository, AdminMapper adminMapper,
                                           AuditService auditService, NotificationService notificationService) {
        this.activationRequestRepository = activationRequestRepository;
        this.investorProfileService = investorProfileService;
        this.userRepository = userRepository;
        this.adminMapper = adminMapper;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Page<AdminInvestorActivationDto> list(String status, int page, int size) {
        InvestorActivationStatus statusEnum = parseOptionalStatus(status);
        Pageable pageable = PageRequest.of(page, AdminPaging.clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InvestorActivationRequest> requests = statusEnum == null
                ? activationRequestRepository.findAllByOrderByCreatedAtDesc(pageable)
                : activationRequestRepository.findByStatusOrderByCreatedAtDesc(statusEnum, pageable);
        Map<String, User> users = fetchReferencedUsers(requests.getContent());
        return requests.map(r -> adminMapper.toDto(r, users.get(r.getRequesterUserId()),
                r.getReviewedBy() == null ? null : users.get(r.getReviewedBy())));
    }

    @Transactional(readOnly = true)
    public AdminInvestorActivationDto get(String id) {
        InvestorActivationRequest request = getEntityOrThrow(id);
        Map<String, User> users = fetchReferencedUsers(java.util.List.of(request));
        return adminMapper.toDto(request, users.get(request.getRequesterUserId()),
                request.getReviewedBy() == null ? null : users.get(request.getReviewedBy()));
    }

    @Transactional
    public AdminInvestorActivationDto approve(String adminId, String id, String ip) {
        InvestorActivationRequest request = requirePending(id);

        var createRequest = new CreateInvestorProfileRequest(
                request.getInvestorType().getLabel(), request.getFirmName(), request.getThesis(),
                request.getSectors(), request.getStages(), request.getGeographies(),
                request.getTicketMin(), request.getTicketMax(), request.getPortfolioCount(), request.getWebsite());
        var profile = investorProfileService.create(request.getRequesterUserId(), createRequest);

        request.setStatus(InvestorActivationStatus.APPROVED);
        request.setResultingProfileId(profile.id());
        request.setReviewedBy(adminId);
        request.setReviewedAt(Instant.now());
        request = activationRequestRepository.saveAndFlush(request);

        auditService.log(adminId, AuditAction.INVESTOR_ACTIVATION_APPROVED, "InvestorActivationRequest", id, ip, Map.of());
        notificationService.notify(request.getRequesterUserId(), NotificationType.investor_activation,
                "Your investor application was approved",
                "You're now a verified investor on Buildadda and can browse startups and request introductions.",
                profile.id(), adminId);

        return toDtoWithUsers(request);
    }

    @Transactional
    public AdminInvestorActivationDto reject(String adminId, String id, String reason, String ip) {
        InvestorActivationRequest request = requirePending(id);

        request.setStatus(InvestorActivationStatus.REJECTED);
        request.setReviewNote(reason);
        request.setReviewedBy(adminId);
        request.setReviewedAt(Instant.now());
        request = activationRequestRepository.saveAndFlush(request);

        auditService.log(adminId, AuditAction.INVESTOR_ACTIVATION_REJECTED, "InvestorActivationRequest", id, ip,
                Map.of("reason", reason));
        notificationService.notify(request.getRequesterUserId(), NotificationType.investor_activation,
                "Your investor application was not approved",
                "Reason: " + reason + ". You can update your details and apply again.",
                null, adminId);

        return toDtoWithUsers(request);
    }

    private InvestorActivationRequest requirePending(String id) {
        InvestorActivationRequest request = getEntityOrThrow(id);
        if (request.getStatus() != InvestorActivationStatus.PENDING) {
            throw new ConflictException("This application has already been reviewed");
        }
        return request;
    }

    private InvestorActivationRequest getEntityOrThrow(String id) {
        return activationRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Investor activation request not found: " + id));
    }

    private InvestorActivationStatus parseOptionalStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return InvestorActivationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + status);
        }
    }

    private AdminInvestorActivationDto toDtoWithUsers(InvestorActivationRequest request) {
        Map<String, User> users = fetchReferencedUsers(java.util.List.of(request));
        return adminMapper.toDto(request, users.get(request.getRequesterUserId()),
                request.getReviewedBy() == null ? null : users.get(request.getReviewedBy()));
    }

    private Map<String, User> fetchReferencedUsers(java.util.List<InvestorActivationRequest> requests) {
        Set<String> ids = new HashSet<>();
        for (InvestorActivationRequest r : requests) {
            ids.add(r.getRequesterUserId());
            if (r.getReviewedBy() != null) ids.add(r.getReviewedBy());
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
    }
}
