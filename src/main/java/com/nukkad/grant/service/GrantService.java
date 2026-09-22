package com.nukkad.grant.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.grant.dto.CreateGrantRequest;
import com.nukkad.grant.dto.DiscoveredGrantCandidate;
import com.nukkad.grant.dto.GrantDto;
import com.nukkad.grant.dto.UpdateGrantRequest;
import com.nukkad.grant.entity.Grant;
import com.nukkad.grant.entity.GrantDiscoveryOrigin;
import com.nukkad.grant.entity.GrantProviderType;
import com.nukkad.grant.mapper.GrantMapper;
import com.nukkad.grant.repository.GrantRepository;
import com.nukkad.grant.repository.GrantSpecifications;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.user.entity.AccountStatus;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class GrantService {

    private final GrantRepository grantRepository;
    private final GrantMapper grantMapper;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public GrantService(GrantRepository grantRepository, GrantMapper grantMapper,
                         NotificationService notificationService, AuditService auditService,
                         UserRepository userRepository) {
        this.grantRepository = grantRepository;
        this.grantMapper = grantMapper;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    public Grant getEntityOrThrow(String id) {
        return grantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grant not found: " + id));
    }

    // PUBLIC getter — 404s a removed grant for everyone, and 404s a PENDING/REJECTED grant for
    // everyone except the person who submitted it (so they can see their own review status).
    @Transactional(readOnly = true)
    public GrantDto getGrant(String id, String viewerId) {
        Grant grant = getEntityOrThrow(id);
        if (grant.isRemovedByAdmin()) {
            throw new ResourceNotFoundException("Grant not found: " + id);
        }
        if (grant.getModerationStatus() != ModerationStatus.APPROVED && !grant.getCreatedByUserId().equals(viewerId)) {
            throw new ResourceNotFoundException("Grant not found: " + id);
        }
        return grantMapper.toDto(grant, canManage(viewerId, grant));
    }

    // Admin-only: bypasses both the removed and moderation-status gates above.
    @Transactional(readOnly = true)
    public GrantDto getGrantForAdmin(String id) {
        Grant grant = getEntityOrThrow(id);
        return grantMapper.toDto(grant, false);
    }

    // PUBLIC listing — always excludes removed and non-approved grants. There's no "my grants"
    // filter on this endpoint (unlike Idea/Startup/Opportunity), so no own-content bypass is
    // needed here; a submitter reaches their own pending/rejected grant via its detail page.
    @Transactional(readOnly = true)
    public Page<GrantDto> listGrants(String q, String providerType, String stage, String sector,
                                      boolean includeExpired, String viewerId, int page, int size) {
        Specification<Grant> spec = GrantSpecifications.combine(
                GrantSpecifications.search(q),
                GrantSpecifications.providerType(providerType),
                GrantSpecifications.stage(stage),
                GrantSpecifications.sector(sector),
                includeExpired ? null : GrantSpecifications.notExpired(),
                GrantSpecifications.notRemoved(),
                GrantSpecifications.approved()
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return grantRepository.findAll(spec, pageable).map(g -> grantMapper.toDto(g, canManage(viewerId, g)));
    }

    // ADMIN-ONLY — never applies the approved()/notRemoved() gates unconditionally; an admin must
    // see every status to review it. includeRemoved and moderationStatus are independent filters.
    @Transactional(readOnly = true)
    public Page<GrantDto> listGrantsForAdmin(String q, String providerType, String stage, String sector,
                                              boolean includeExpired, boolean includeRemoved,
                                              ModerationStatus moderationStatus, int page, int size) {
        Specification<Grant> spec = GrantSpecifications.combine(
                GrantSpecifications.search(q),
                GrantSpecifications.providerType(providerType),
                GrantSpecifications.stage(stage),
                GrantSpecifications.sector(sector),
                includeExpired ? null : GrantSpecifications.notExpired(),
                includeRemoved ? null : GrantSpecifications.notRemoved(),
                GrantSpecifications.moderationStatus(moderationStatus)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return grantRepository.findAll(spec, pageable).map(g -> grantMapper.toDto(g, false));
    }

    // Admin-only moderation toggle: hides a grant from public discovery without deleting it, or
    // reverses that. Mirrors IdeaService.setRemovedByAdmin exactly.
    @Transactional
    public GrantDto setRemovedByAdmin(String adminId, String grantId, boolean removed, String reason, String ip) {
        Grant grant = getEntityOrThrow(grantId);
        grant.setRemovedByAdmin(removed);
        grant.setRemovalReason(removed ? reason : null);
        grant = grantRepository.saveAndFlush(grant);

        Map<String, Object> details = new HashMap<>();
        details.put("entityType", "Grant");
        if (removed && reason != null && !reason.isBlank()) details.put("reason", reason);
        auditService.log(adminId, removed ? AuditAction.ADMIN_CONTENT_REMOVED : AuditAction.ADMIN_CONTENT_RESTORED,
                "Grant", grantId, ip, details);

        return grantMapper.toDto(grant, false);
    }

    // Pre-publish approval gate: a grant may be reviewed exactly once (PENDING -> APPROVED/REJECTED)
    // — see IdeaService.reviewModeration for the identical rationale.
    @Transactional
    public GrantDto reviewModeration(String adminId, String grantId, boolean approved, String reason, String ip) {
        Grant grant = getEntityOrThrow(grantId);
        if (grant.getModerationStatus() != ModerationStatus.PENDING) {
            throw new ConflictException("This grant has already been reviewed");
        }
        if (!approved && (reason == null || reason.isBlank())) {
            throw new BadRequestException("A reason is required when rejecting a grant");
        }

        grant.setModerationStatus(approved ? ModerationStatus.APPROVED : ModerationStatus.REJECTED);
        grant.setRejectionReason(approved ? null : reason);
        grant.setModerationReviewedBy(adminId);
        grant.setModerationReviewedAt(Instant.now());
        grant = grantRepository.saveAndFlush(grant);

        auditService.log(adminId, approved ? AuditAction.ADMIN_CONTENT_APPROVED : AuditAction.ADMIN_CONTENT_REJECTED,
                "Grant", grantId, ip, approved ? Map.of() : Map.of("reason", reason));

        notificationService.notify(grant.getCreatedByUserId(), NotificationType.grant,
                approved ? "Your grant listing was approved" : "Your grant listing was not approved",
                approved ? "\"" + grant.getName() + "\" is now visible to the community."
                        : "\"" + grant.getName() + "\" was not approved: " + reason,
                grantId, adminId);

        return grantMapper.toDto(grant, false);
    }

    @Transactional
    public GrantDto createGrant(String userId, CreateGrantRequest request) {
        Grant grant = grantRepository.saveAndFlush(buildGrant(userId, request, ModerationStatus.PENDING));
        return grantMapper.toDto(grant, true);
    }

    /**
     * An admin publishing a grant listing from the admin panel. With {@code createdByEmail}, that member is
     * attributed as its creator (and is told, since it now appears as theirs); without it the admin's own
     * account is. Unlike a member's own submission, this is live immediately rather than entering the
     * pending-moderation queue.
     */
    @Transactional
    public GrantDto createGrantAsAdmin(String adminId, CreateGrantRequest request, String createdByEmail, String ip) {
        String creatorId = resolveCreator(adminId, createdByEmail);

        Grant grant = grantRepository.saveAndFlush(buildGrant(creatorId, request, ModerationStatus.APPROVED));

        auditService.log(adminId, AuditAction.ADMIN_GRANT_CREATED, "Grant", grant.getId(), ip, Map.of());

        if (!creatorId.equals(adminId)) {
            notificationService.notify(creatorId, NotificationType.grant, "A grant listing was posted for you",
                    "\"" + grant.getName() + "\" was posted to BuildAdda for you.", grant.getId(), adminId);
        }
        return grantMapper.toDto(grant, true);
    }

    /**
     * A scheduled AI-discovery run (see GrantDiscoveryService) publishing a newly found scheme.
     * Always live immediately, since the discovery pipeline runs with no admin-review step -- the
     * candidate has already passed that service's own validation (real provider type, a verified
     * source URL, a not-yet-expired deadline) before it ever reaches here. Attributed to the
     * platform's own system admin account, never a member -- there's no "posted for you" concept
     * for something nobody submitted.
     */
    @Transactional
    public GrantDto createGrantFromDiscovery(DiscoveredGrantCandidate candidate, String systemAdminId, String batchLabel) {
        Grant grant = Grant.builder()
                .name(candidate.name())
                .provider(candidate.provider())
                .providerType(candidate.providerType())
                .description(candidate.description())
                .fundingAmount(candidate.fundingAmount())
                .eligibilityCriteria(candidate.eligibilityCriteria())
                .eligibleSectors(new HashSet<>(candidate.eligibleSectors()))
                .eligibleStages(new HashSet<>(candidate.eligibleStages()))
                .deadline(candidate.deadline())
                .applicationUrl(candidate.applicationUrl())
                .sourceUrl(candidate.sourceUrl())
                .discoveryOrigin(GrantDiscoveryOrigin.AI_DISCOVERY)
                .lastVerifiedAt(Instant.now())
                .createdByUserId(systemAdminId)
                .moderationStatus(ModerationStatus.APPROVED)
                .build();
        grant = grantRepository.saveAndFlush(grant);

        auditService.log(systemAdminId, AuditAction.AI_GRANT_DISCOVERED, "Grant", grant.getId(),
                "internal:grant-discovery", Map.of("batch", batchLabel));
        return grantMapper.toDto(grant, false);
    }

    /** Re-verification of an existing AI-discovered grant on a later run -- refreshes the mutable
     *  details (a deadline can move, a funding cap can change) and bumps lastVerifiedAt, but never
     *  touches moderationStatus/createdByUserId/discoveryOrigin: this only ever updates a row that
     *  createGrantFromDiscovery already created. */
    @Transactional
    public GrantDto refreshGrantFromDiscovery(String grantId, DiscoveredGrantCandidate candidate,
                                               String systemAdminId, String batchLabel) {
        Grant grant = getEntityOrThrow(grantId);
        grant.setDescription(candidate.description());
        grant.setFundingAmount(candidate.fundingAmount());
        grant.setEligibilityCriteria(candidate.eligibilityCriteria());
        grant.setEligibleSectors(new HashSet<>(candidate.eligibleSectors()));
        grant.setEligibleStages(new HashSet<>(candidate.eligibleStages()));
        grant.setDeadline(candidate.deadline());
        grant.setSourceUrl(candidate.sourceUrl());
        grant.setLastVerifiedAt(Instant.now());
        grant = grantRepository.saveAndFlush(grant);

        auditService.log(systemAdminId, AuditAction.AI_GRANT_VERIFIED, "Grant", grant.getId(),
                "internal:grant-discovery", Map.of("batch", batchLabel));
        return grantMapper.toDto(grant, false);
    }

    private String resolveCreator(String adminId, String createdByEmail) {
        if (createdByEmail == null || createdByEmail.isBlank()) return adminId;
        User creator = userRepository.findByEmail(createdByEmail.toLowerCase().trim())
                .orElseThrow(() -> new BadRequestException("No member has that email address"));
        if (creator.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("That member's account is not active");
        }
        return creator.getId();
    }

    private Grant buildGrant(String createdByUserId, CreateGrantRequest request, ModerationStatus status) {
        return Grant.builder()
                .name(request.name().trim())
                .provider(request.provider().trim())
                .providerType(parseProviderType(request.providerType()))
                .description(request.description())
                .fundingAmount(request.fundingAmount())
                .eligibilityCriteria(request.eligibilityCriteria())
                .eligibleSectors(request.eligibleSectors() == null ? new HashSet<>() : new HashSet<>(request.eligibleSectors()))
                .eligibleStages(parseStages(request.eligibleStages()))
                .deadline(request.deadline())
                .applicationUrl(normalizeUrl(request.applicationUrl()))
                .createdByUserId(createdByUserId)
                .moderationStatus(status)
                .build();
    }

    @Transactional
    public GrantDto updateGrant(String userId, String id, UpdateGrantRequest request) {
        Grant grant = getEntityOrThrow(id);
        requireManager(userId, grant);

        if (request.name() != null) grant.setName(request.name().trim());
        if (request.provider() != null) grant.setProvider(request.provider().trim());
        if (request.providerType() != null) grant.setProviderType(parseProviderType(request.providerType()));
        if (request.description() != null) grant.setDescription(request.description());
        if (request.fundingAmount() != null) grant.setFundingAmount(request.fundingAmount());
        if (request.eligibilityCriteria() != null) grant.setEligibilityCriteria(request.eligibilityCriteria());
        if (request.eligibleSectors() != null) grant.setEligibleSectors(new HashSet<>(request.eligibleSectors()));
        if (request.eligibleStages() != null) grant.setEligibleStages(parseStages(request.eligibleStages()));
        if (request.deadline() != null) grant.setDeadline(request.deadline());
        if (request.applicationUrl() != null) grant.setApplicationUrl(normalizeUrl(request.applicationUrl()));

        return grantMapper.toDto(grantRepository.saveAndFlush(grant), true);
    }

    @Transactional
    public void deleteGrant(String userId, String id) {
        Grant grant = getEntityOrThrow(id);
        requireManager(userId, grant);
        grantRepository.delete(grant);
    }

    private GrantProviderType parseProviderType(String label) {
        try {
            return GrantProviderType.fromLabel(label);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown provider type: " + label);
        }
    }

    private HashSet<StartupStage> parseStages(List<String> labels) {
        if (labels == null) return new HashSet<>();
        HashSet<StartupStage> stages = new HashSet<>();
        for (String label : labels) {
            try {
                stages.add(StartupStage.fromLabel(label));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown stage: " + label);
            }
        }
        return stages;
    }

    /** Mirrors ResourceService's scheme-optional normalization — grant curators often paste a bare domain. */
    private String normalizeUrl(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isEmpty()) throw new BadRequestException("Application URL is required");
        String candidate = trimmed.matches("(?i)^https?://.*") ? trimmed : "https://" + trimmed;
        try {
            URI uri = new URI(candidate);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BadRequestException("Application URL is not a valid URL");
            }
        } catch (URISyntaxException e) {
            throw new BadRequestException("Application URL is not a valid URL");
        }
        return candidate;
    }

    private boolean canManage(String userId, Grant grant) {
        return userId != null && userId.equals(grant.getCreatedByUserId());
    }

    private void requireManager(String userId, Grant grant) {
        if (!canManage(userId, grant)) {
            throw new ForbiddenException("Only the person who added this grant can perform this action");
        }
    }
}
