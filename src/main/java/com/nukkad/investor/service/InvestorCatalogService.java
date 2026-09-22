package com.nukkad.investor.service;

import com.nukkad.admin.dto.AdminInvestorDto;
import com.nukkad.admin.dto.UpdateInvestorRequest;
import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.investor.dto.CreateIntroRequestRequest;
import com.nukkad.investor.dto.InvestorDto;
import com.nukkad.investor.dto.InvestorIntroRequestDto;
import com.nukkad.investor.dto.InvestorIntroductionResultDto;
import com.nukkad.investor.dto.RequestInvestorIntroductionRequest;
import com.nukkad.investor.entity.Investor;
import com.nukkad.investor.entity.InvestorIntroRequest;
import com.nukkad.investor.entity.InvestorIntroRequestStatus;
import com.nukkad.investor.entity.InvestorProfile;
import com.nukkad.investor.entity.InvestorType;
import com.nukkad.investor.mapper.InvestorMapper;
import com.nukkad.investor.repository.InvestorIntroRequestRepository;
import com.nukkad.investor.repository.InvestorProfileRepository;
import com.nukkad.investor.repository.InvestorRepository;
import com.nukkad.investor.repository.InvestorSpecifications;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.service.StartupAccessPolicy;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Investor Discovery: an admin-managed catalog of investor records that founders search, filter and request
 * introductions from. A founder never creates or owns one of these rows (see the {@link Investor} class comment
 * for how this relates to the pre-existing, user-owned {@link InvestorProfile} accounts) — only an admin adds,
 * edits, or retires one. Access to every founder-facing method here requires an active Startup Profile
 * ({@link StartupAccessPolicy#hasActiveStartup}); nothing checks a role for the admin-only methods below —
 * those endpoints sit under {@code /api/admin/**}, which the security configuration already restricts to
 * admin-portal tokens (same convention as {@code ResourceService}).
 */
@Service
public class InvestorCatalogService {

    /** What an admin fills in to add a catalog investor by hand — see InvestorImportWorker for how a CSV row
     *  becomes an Investor instead. */
    public record NewInvestor(String name, String investorType, String description, String location, String website,
                               Set<String> sectors, Set<String> stages, Long chequeMin, Long chequeMax,
                               boolean active, boolean visible, String linkedInvestorProfileId,
                               String country, String domain, Set<String> programs, Set<String> keyPeople,
                               Integer investmentCount, Integer exitCount,
                               String facebookUrl, String instagramUrl, String linkedinUrl, String twitterUrl,
                               String contactEmail, Boolean contactEmailVerified, String secondaryEmail, String phoneNumber) {}

    private final InvestorRepository investorRepository;
    private final InvestorIntroRequestRepository investorIntroRequestRepository;
    private final InvestorProfileRepository investorProfileRepository;
    private final StartupRepository startupRepository;
    private final StartupAccessPolicy startupAccessPolicy;
    private final IntroRequestService introRequestService;
    private final UserRepository userRepository;
    private final InvestorMapper investorMapper;
    private final AdminMapper adminMapper;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public InvestorCatalogService(InvestorRepository investorRepository,
                                   InvestorIntroRequestRepository investorIntroRequestRepository,
                                   InvestorProfileRepository investorProfileRepository,
                                   StartupRepository startupRepository,
                                   StartupAccessPolicy startupAccessPolicy,
                                   IntroRequestService introRequestService,
                                   UserRepository userRepository,
                                   InvestorMapper investorMapper,
                                   AdminMapper adminMapper,
                                   FileStorageService fileStorageService,
                                   AuditService auditService) {
        this.investorRepository = investorRepository;
        this.investorIntroRequestRepository = investorIntroRequestRepository;
        this.investorProfileRepository = investorProfileRepository;
        this.startupRepository = startupRepository;
        this.startupAccessPolicy = startupAccessPolicy;
        this.introRequestService = introRequestService;
        this.userRepository = userRepository;
        this.investorMapper = investorMapper;
        this.adminMapper = adminMapper;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    public Investor getEntityOrThrow(String id) {
        return investorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Investor not found: " + id));
    }

    // ---- Founder-facing (every method requires an active Startup Profile — see the class comment) ----

    /** Whether {@code viewerId} may use Investor Discovery at all — the frontend calls this once to decide
     *  between the locked state and the real page, and every other method here enforces it again itself. */
    public boolean hasAccess(String viewerId) {
        return startupAccessPolicy.hasActiveStartup(viewerId);
    }

    @Transactional(readOnly = true)
    public Page<InvestorDto> list(String type, String sector, String stage, String location, String country, Long chequeSize,
                                   String q, String viewerId, int page, int size) {
        requireAccess(viewerId);
        Specification<Investor> spec = InvestorSpecifications.combine(
                InvestorSpecifications.publiclyVisible(),
                InvestorSpecifications.search(q),
                InvestorSpecifications.type(type),
                InvestorSpecifications.sector(sector),
                InvestorSpecifications.stage(stage),
                InvestorSpecifications.location(location),
                InvestorSpecifications.country(country),
                InvestorSpecifications.chequeSize(chequeSize)
        );
        // createdAt only has second precision, so break ties on id — otherwise rows created together can repeat or vanish between pages.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return investorRepository.findAll(spec, pageable).map(investorMapper::toDto);
    }

    @Transactional(readOnly = true)
    public InvestorDto get(String id, String viewerId) {
        requireAccess(viewerId);
        Investor investor = getEntityOrThrow(id);
        // A hidden or deactivated investor is a 404, not a 403 — a founder should never learn it exists.
        if (!investor.isPubliclyVisible()) throw new ResourceNotFoundException("Investor not found: " + id);
        return investorMapper.toDto(investor);
    }

    @Transactional
    public InvestorIntroductionResultDto requestIntroduction(String founderId, String investorId, RequestInvestorIntroductionRequest request) {
        requireAccess(founderId);
        Investor investor = getEntityOrThrow(investorId);
        if (!investor.isPubliclyVisible()) throw new ResourceNotFoundException("Investor not found: " + investorId);

        Startup startup = startupAccessPolicy.requireReadable(request.startupId(), founderId);
        if (!startupAccessPolicy.canManage(startup.getId(), founderId)) {
            throw new ForbiddenException("You can only request an introduction on behalf of a startup you manage");
        }

        String linkedProfileId = investor.getLinkedInvestorProfileId();
        InvestorProfile linkedProfile = linkedProfileId == null ? null : investorProfileRepository.findById(linkedProfileId).orElse(null);

        if (linkedProfile != null) {
            // This catalog row is tied to a real, activated investor account — reuse the existing introduction
            // pipeline exactly (dedupe, notification, and a conversation once accepted) rather than duplicating it.
            var live = introRequestService.create(founderId, new CreateIntroRequestRequest(
                    linkedProfile.getUserId(), "FOUNDER_TO_INVESTOR", startup.getId(), null, request.message()));
            return new InvestorIntroductionResultDto("LIVE", live, null);
        }

        // No live account behind this row (the common case): record the request for an admin to follow up on.
        if (investorIntroRequestRepository.existsByInvestorIdAndStartupIdAndStatus(investorId, startup.getId(), InvestorIntroRequestStatus.PENDING)) {
            throw new ConflictException("There is already a pending introduction request to this investor for this startup");
        }
        InvestorIntroRequest entity = InvestorIntroRequest.builder()
                .investorId(investorId)
                .requesterUserId(founderId)
                .startupId(startup.getId())
                .message(request.message().trim())
                .build();
        entity = investorIntroRequestRepository.saveAndFlush(entity);
        auditService.log(founderId, AuditAction.INVESTOR_INTRODUCTION, "InvestorIntroRequest", entity.getId(), null);

        return new InvestorIntroductionResultDto("RECORDED", null, toIntroDto(entity));
    }

    private void requireAccess(String viewerId) {
        if (!hasAccess(viewerId)) {
            throw new ForbiddenException("Create your Startup Profile to discover investors");
        }
    }

    // ---- Admin-only (see the class comment — no role check belongs here) ----

    @Transactional(readOnly = true)
    public Page<AdminInvestorDto> listForAdmin(String q, String type, Boolean active, Boolean visible, int page, int size) {
        Specification<Investor> spec = InvestorSpecifications.combine(
                InvestorSpecifications.search(q),
                InvestorSpecifications.type(type),
                active == null ? null : (root, query, cb) -> cb.equal(root.get("active"), active),
                visible == null ? null : (root, query, cb) -> cb.equal(root.get("visible"), visible)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return investorRepository.findAll(spec, pageable).map(this::toAdminDto);
    }

    @Transactional(readOnly = true)
    public AdminInvestorDto getForAdmin(String id) {
        return toAdminDto(getEntityOrThrow(id));
    }

    @Transactional
    public AdminInvestorDto create(String adminId, NewInvestor in, MultipartFile logo, String ip) {
        InvestorType type = parseType(in.investorType());
        validateChequeRange(in.chequeMin(), in.chequeMax());
        String linkedId = resolveLinkedProfileId(in.linkedInvestorProfileId());

        String logoUrl = (logo != null && !logo.isEmpty()) ? fileStorageService.storeImage(logo, "investor-logos") : null;

        Investor investor = Investor.builder()
                .name(in.name().trim())
                .investorType(type)
                .description(blankToNull(in.description()))
                .location(blankToNull(in.location()))
                .country(blankToNull(in.country()))
                .website(blankToNull(in.website()))
                .domain(blankToNull(in.domain()))
                .logoUrl(logoUrl)
                .sectors(in.sectors() == null ? new HashSet<>() : new HashSet<>(in.sectors()))
                .stages(in.stages() == null ? new HashSet<>() : new HashSet<>(in.stages()))
                .programs(in.programs() == null ? new HashSet<>() : new HashSet<>(in.programs()))
                .keyPeople(in.keyPeople() == null ? new HashSet<>() : new HashSet<>(in.keyPeople()))
                .investmentCount(in.investmentCount())
                .exitCount(in.exitCount())
                .chequeMin(in.chequeMin())
                .chequeMax(in.chequeMax())
                .active(in.active())
                .visible(in.visible())
                .facebookUrl(blankToNull(in.facebookUrl()))
                .instagramUrl(blankToNull(in.instagramUrl()))
                .linkedinUrl(blankToNull(in.linkedinUrl()))
                .twitterUrl(blankToNull(in.twitterUrl()))
                .contactEmail(blankToNull(in.contactEmail()))
                .contactEmailVerified(in.contactEmailVerified())
                .secondaryEmail(blankToNull(in.secondaryEmail()))
                .phoneNumber(blankToNull(in.phoneNumber()))
                .linkedInvestorProfileId(linkedId)
                .createdByAdminId(adminId)
                .build();
        investor = investorRepository.saveAndFlush(investor);

        auditService.log(adminId, AuditAction.ADMIN_INVESTOR_CREATED, "Investor", investor.getId(), ip, Map.of("name", investor.getName()));
        return toAdminDto(investor);
    }

    @Transactional
    public AdminInvestorDto update(String adminId, String id, UpdateInvestorRequest request, String ip) {
        Investor investor = getEntityOrThrow(id);

        if (request.name() != null) {
            if (request.name().isBlank()) throw new BadRequestException("Name cannot be blank");
            investor.setName(request.name().trim());
        }
        if (request.investorType() != null) investor.setInvestorType(parseType(request.investorType()));
        if (request.description() != null) investor.setDescription(blankToNull(request.description()));
        if (request.location() != null) investor.setLocation(blankToNull(request.location()));
        if (request.country() != null) investor.setCountry(blankToNull(request.country()));
        if (request.website() != null) investor.setWebsite(blankToNull(request.website()));
        if (request.domain() != null) investor.setDomain(blankToNull(request.domain()));
        if (request.sectors() != null) investor.setSectors(new HashSet<>(request.sectors()));
        if (request.stages() != null) investor.setStages(new HashSet<>(request.stages()));
        if (request.programs() != null) investor.setPrograms(new HashSet<>(request.programs()));
        if (request.keyPeople() != null) investor.setKeyPeople(new HashSet<>(request.keyPeople()));
        if (request.investmentCount() != null) investor.setInvestmentCount(request.investmentCount());
        if (request.exitCount() != null) investor.setExitCount(request.exitCount());
        if (request.chequeMin() != null) investor.setChequeMin(request.chequeMin());
        if (request.chequeMax() != null) investor.setChequeMax(request.chequeMax());
        validateChequeRange(investor.getChequeMin(), investor.getChequeMax());
        if (request.active() != null) investor.setActive(request.active());
        if (request.visible() != null) investor.setVisible(request.visible());
        if (request.facebookUrl() != null) investor.setFacebookUrl(blankToNull(request.facebookUrl()));
        if (request.instagramUrl() != null) investor.setInstagramUrl(blankToNull(request.instagramUrl()));
        if (request.linkedinUrl() != null) investor.setLinkedinUrl(blankToNull(request.linkedinUrl()));
        if (request.twitterUrl() != null) investor.setTwitterUrl(blankToNull(request.twitterUrl()));
        if (request.contactEmail() != null) investor.setContactEmail(blankToNull(request.contactEmail()));
        if (request.contactEmailVerified() != null) investor.setContactEmailVerified(request.contactEmailVerified());
        if (request.secondaryEmail() != null) investor.setSecondaryEmail(blankToNull(request.secondaryEmail()));
        if (request.phoneNumber() != null) investor.setPhoneNumber(blankToNull(request.phoneNumber()));
        if (request.linkedInvestorProfileId() != null) {
            investor.setLinkedInvestorProfileId(resolveLinkedProfileId(request.linkedInvestorProfileId()));
        }

        investor = investorRepository.saveAndFlush(investor);
        auditService.log(adminId, AuditAction.ADMIN_INVESTOR_UPDATED, "Investor", investor.getId(), ip, Map.of("name", investor.getName()));
        return toAdminDto(investor);
    }

    @Transactional
    public void delete(String adminId, String id, String ip) {
        Investor investor = getEntityOrThrow(id);
        String name = investor.getName();
        String logoUrl = investor.getLogoUrl();
        investorRepository.delete(investor);
        auditService.log(adminId, AuditAction.ADMIN_INVESTOR_DELETED, "Investor", id, ip, Map.of("name", name));
        fileStorageService.deleteIfHosted(logoUrl);
    }

    @Transactional
    public AdminInvestorDto replaceLogo(String adminId, String id, MultipartFile logo, String ip) {
        Investor investor = getEntityOrThrow(id);
        String previous = investor.getLogoUrl();
        investor.setLogoUrl(fileStorageService.storeImage(logo, "investor-logos"));
        investor = investorRepository.saveAndFlush(investor);
        auditService.log(adminId, AuditAction.ADMIN_INVESTOR_UPDATED, "Investor", investor.getId(), ip, Map.of("logo", "replaced"));
        fileStorageService.deleteIfHosted(previous);
        return toAdminDto(investor);
    }

    @Transactional
    public AdminInvestorDto removeLogo(String adminId, String id, String ip) {
        Investor investor = getEntityOrThrow(id);
        String previous = investor.getLogoUrl();
        investor.setLogoUrl(null);
        investor = investorRepository.saveAndFlush(investor);
        auditService.log(adminId, AuditAction.ADMIN_INVESTOR_UPDATED, "Investor", investor.getId(), ip, Map.of("logo", "removed"));
        fileStorageService.deleteIfHosted(previous);
        return toAdminDto(investor);
    }

    @Transactional(readOnly = true)
    public Page<InvestorIntroRequestDto> listIntroRequestsForAdmin(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InvestorIntroRequest> requests = (status == null || status.isBlank())
                ? investorIntroRequestRepository.findAllByOrderByCreatedAtDesc(pageable)
                : investorIntroRequestRepository.findByStatusOrderByCreatedAtDesc(parseIntroStatus(status), pageable);
        return requests.map(this::toIntroDto);
    }

    /** Marks a recorded request as followed-up (however the admin contacted the investor outside the app),
     *  which frees the founder to request again later. */
    @Transactional
    public InvestorIntroRequestDto closeIntroRequest(String adminId, String id, String ip) {
        InvestorIntroRequest request = investorIntroRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Introduction request not found: " + id));
        if (request.getStatus() == InvestorIntroRequestStatus.CLOSED) {
            throw new ConflictException("This request is already closed");
        }
        request.setStatus(InvestorIntroRequestStatus.CLOSED);
        request.setClosedByAdminId(adminId);
        request.setClosedAt(Instant.now());
        request = investorIntroRequestRepository.saveAndFlush(request);
        auditService.log(adminId, AuditAction.ADMIN_INVESTOR_INTRO_CLOSED, "InvestorIntroRequest", request.getId(), ip, Map.of());
        return toIntroDto(request);
    }

    // ---- helpers ----

    private String resolveLinkedProfileId(String rawId) {
        if (rawId == null || rawId.isBlank()) return null;
        if (!investorProfileRepository.existsById(rawId)) {
            throw new ResourceNotFoundException("Investor profile not found: " + rawId);
        }
        return rawId;
    }

    private void validateChequeRange(Long min, Long max) {
        if (min != null && max != null && min > max) {
            throw new BadRequestException("Minimum cheque size cannot be greater than the maximum");
        }
    }

    private static InvestorType parseType(String label) {
        try {
            return InvestorType.fromLabel(label);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown investor type: " + label);
        }
    }

    private static InvestorIntroRequestStatus parseIntroStatus(String status) {
        try {
            return InvestorIntroRequestStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + status);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AdminInvestorDto toAdminDto(Investor investor) {
        String linkedName = null;
        if (investor.getLinkedInvestorProfileId() != null) {
            linkedName = investorProfileRepository.findById(investor.getLinkedInvestorProfileId())
                    .flatMap(p -> userRepository.findById(p.getUserId()))
                    .map(User::getName)
                    .orElse(null);
        }
        return adminMapper.toDto(investor, linkedName);
    }

    private InvestorIntroRequestDto toIntroDto(InvestorIntroRequest request) {
        String investorName = investorRepository.findById(request.getInvestorId()).map(Investor::getName).orElse(null);
        String requesterName = userRepository.findById(request.getRequesterUserId()).map(User::getName).orElse(null);
        String startupName = startupRepository.findById(request.getStartupId()).map(Startup::getName).orElse(null);
        return investorMapper.toDto(request, investorName, requesterName, startupName);
    }
}
