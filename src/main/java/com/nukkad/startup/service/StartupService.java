package com.nukkad.startup.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.startup.dto.CreateStartupRequest;
import com.nukkad.startup.dto.CreateStartupRoleRequest;
import com.nukkad.startup.dto.StartupDto;
import com.nukkad.startup.dto.StartupJoinRequestDto;
import com.nukkad.startup.dto.StartupMaterialDto;
import com.nukkad.startup.dto.StartupRoleDto;
import com.nukkad.startup.dto.StartupSectorDto;
import com.nukkad.startup.dto.StartupTeamMemberDto;
import com.nukkad.startup.dto.StartupUpdateDto;
import com.nukkad.startup.dto.UpdateStartupRequest;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupFollow;
import com.nukkad.startup.entity.StartupMaterial;
import com.nukkad.startup.entity.StartupMaterialType;
import com.nukkad.startup.entity.StartupProfileView;
import com.nukkad.startup.entity.StartupRole;
import com.nukkad.startup.entity.StartupRoleType;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.entity.StartupUpdate;
import com.nukkad.startup.entity.StartupVisibility;
import com.nukkad.startup.mapper.StartupMapper;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.opportunity.repository.OpportunityRepository;
import com.nukkad.startup.repository.StartupFollowRepository;
import com.nukkad.startup.repository.StartupMaterialRepository;
import com.nukkad.startup.repository.StartupProfileViewRepository;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupRoleRepository;
import com.nukkad.startup.repository.StartupSpecifications;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import com.nukkad.startup.repository.StartupUpdateRepository;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.AccountStatus;
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
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StartupService {

    /** Founder + Admin — the tier that unlocks edit-startup/manage-team/post-jobs/edit-fundraising. */
    private static final List<StartupTeamMember.TeamRole> MANAGER_ROLES =
            List.of(StartupTeamMember.TeamRole.FOUNDER, StartupTeamMember.TeamRole.ADMIN);

    private final StartupRepository startupRepository;
    private final StartupTeamMemberRepository teamMemberRepository;
    private final StartupUpdateRepository updateRepository;
    private final StartupRoleRepository roleRepository;
    private final StartupFollowRepository followRepository;
    private final StartupMaterialRepository materialRepository;
    private final StartupProfileViewRepository profileViewRepository;
    private final UserRepository userRepository;
    private final OpportunityRepository opportunityRepository;
    private final IdeaRepository ideaRepository;
    private final UserService userService;
    private final StartupMapper startupMapper;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final StartupAccessPolicy accessPolicy;

    public StartupService(StartupRepository startupRepository,
                           StartupTeamMemberRepository teamMemberRepository,
                           StartupUpdateRepository updateRepository,
                           StartupRoleRepository roleRepository,
                           StartupFollowRepository followRepository,
                           StartupMaterialRepository materialRepository,
                           StartupProfileViewRepository profileViewRepository,
                           UserRepository userRepository,
                           OpportunityRepository opportunityRepository,
                           IdeaRepository ideaRepository,
                           UserService userService,
                           StartupMapper startupMapper,
                           NotificationService notificationService,
                           FileStorageService fileStorageService,
                           AuditService auditService,
                           StartupAccessPolicy accessPolicy) {
        this.startupRepository = startupRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.updateRepository = updateRepository;
        this.roleRepository = roleRepository;
        this.followRepository = followRepository;
        this.materialRepository = materialRepository;
        this.profileViewRepository = profileViewRepository;
        this.userRepository = userRepository;
        this.opportunityRepository = opportunityRepository;
        this.ideaRepository = ideaRepository;
        this.userService = userService;
        this.startupMapper = startupMapper;
        this.notificationService = notificationService;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
        this.accessPolicy = accessPolicy;
    }

    public Startup getEntityOrThrow(String id) {
        return startupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Startup not found: " + id));
    }

    @Transactional(readOnly = true)
    public StartupDto getStartup(String id, String viewerId) {
        // The same gate every sub-resource read below goes through (removed / member-only / not approved).
        Startup startup = accessPolicy.requireReadable(id, viewerId);
        return startupMapper.toDto(startup,
                viewerId != null && followRepository.existsByUserIdAndStartupId(viewerId, id),
                canManageStartup(viewerId, id),
                canViewFundraising(startup, viewerId),
                followRepository.countByStartupId(id));
    }

    // Admin-only: bypasses both the visibility and removed-by-admin checks above so a hidden or
    // removed startup can still be reviewed from the admin panel.
    @Transactional(readOnly = true)
    public StartupDto getStartupForAdmin(String id) {
        Startup startup = getEntityOrThrow(id);
        return manageDto(startup);
    }

    /** Runs as its own transaction so a view never fails (or blocks) the read it's attached to.
     *  Skips the startup's own team so founders/admins/members browsing their own page don't
     *  inflate the "external interest" signal the Founder Dashboard surfaces. */
    @Transactional
    public void recordProfileView(String startupId, String viewerId) {
        if (viewerId != null && isActiveTeamMember(viewerId, startupId)) return;
        if (!startupRepository.existsById(startupId)) return;
        profileViewRepository.save(StartupProfileView.builder().startupId(startupId).viewerId(viewerId).build());
    }

    // PUBLIC listing — excludes non-approved content unless the caller is explicitly filtering to
    // startups they're a member of (memberId == viewerId). PersonProfilePage reuses this same
    // endpoint (not a separate "my startups" endpoint) to show a user's own pending/rejected
    // startups on their own profile, so that case must stay visible to them.
    @Transactional(readOnly = true)
    public Page<StartupDto> listStartups(String q, String sector, String stage, Boolean isRaising,
                                          String chapterId, String memberId, String viewerId, int page, int size) {
        boolean ownContent = memberId != null && memberId.equals(viewerId);
        Specification<Startup> spec = StartupSpecifications.combine(
                StartupSpecifications.search(q),
                StartupSpecifications.sector(sector),
                StartupSpecifications.stage(stage),
                // "Raising" as this viewer may see it: a startup with hidden fundraising must not be revealed by the filter.
                StartupSpecifications.isRaisingAsSeenBy(isRaising, viewerId),
                StartupSpecifications.chapterId(chapterId),
                StartupSpecifications.memberId(memberId),
                StartupSpecifications.visibleTo(viewerId != null),
                StartupSpecifications.notRemoved(),
                ownContent ? null : StartupSpecifications.approved()
        );
        // Newest first; the id breaks ties (timestamps are whole seconds), so a startup can't repeat or vanish between pages.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return toDtoPage(startupRepository.findAll(spec, pageable), viewerId);
    }

    // ADMIN-ONLY — never applies the approved() gate; an admin must see PENDING/REJECTED startups
    // to review them. includeRemoved and moderationStatus are independent, optional narrowing filters.
    @Transactional(readOnly = true)
    public Page<StartupDto> listStartupsForAdmin(String q, String sector, String stage, Boolean isRaising, String chapterId,
                                                  String memberId, String viewerId, boolean includeRemoved,
                                                  ModerationStatus moderationStatus, int page, int size) {
        Specification<Startup> spec = StartupSpecifications.combine(
                StartupSpecifications.search(q),
                StartupSpecifications.sector(sector),
                StartupSpecifications.stage(stage),
                StartupSpecifications.isRaising(isRaising),
                StartupSpecifications.chapterId(chapterId),
                StartupSpecifications.memberId(memberId),
                StartupSpecifications.visibleTo(viewerId != null),
                includeRemoved ? null : StartupSpecifications.notRemoved(),
                StartupSpecifications.moderationStatus(moderationStatus)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return toDtoPage(startupRepository.findAll(spec, pageable), viewerId);
    }

    // See IdeaService.setRemovedByAdmin for the shared rationale behind this moderation model.
    @Transactional
    public StartupDto setRemovedByAdmin(String adminId, String startupId, boolean removed, String reason, String ip) {
        Startup startup = getEntityOrThrow(startupId);
        startup.setRemovedByAdmin(removed);
        startup.setRemovalReason(removed ? reason : null);
        startup = startupRepository.saveAndFlush(startup);

        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("entityType", "Startup");
        if (removed && reason != null && !reason.isBlank()) details.put("reason", reason);
        auditService.log(adminId, removed ? AuditAction.ADMIN_CONTENT_REMOVED : AuditAction.ADMIN_CONTENT_RESTORED,
                "Startup", startupId, ip, details);

        return manageDto(startup);
    }

    @Transactional(readOnly = true)
    public List<StartupDto> listMyFoundedStartups(String userId) {
        List<String> startupIds = teamMemberRepository.findByUserIdAndTeamRoleInAndStatus(userId, MANAGER_ROLES, StartupTeamMember.Status.ACTIVE)
                .stream().map(StartupTeamMember::getStartupId).toList();
        List<Startup> startups = startupRepository.findAllById(startupIds);
        Map<String, Long> followers = followerCounts(startups.stream().map(Startup::getId).toList());
        return startups.stream()
                .map(s -> startupMapper.toDto(s, false, true, true, followers.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    /** A page of startups as the viewer sees them; follower counts come from one query for the whole page. */
    private Page<StartupDto> toDtoPage(Page<Startup> page, String viewerId) {
        Map<String, Long> followers = followerCounts(page.getContent().stream().map(Startup::getId).toList());
        return page.map(s -> startupMapper.toDto(s,
                viewerId != null && followRepository.existsByUserIdAndStartupId(viewerId, s.getId()),
                canManageStartup(viewerId, s.getId()),
                canViewFundraising(s, viewerId),
                followers.getOrDefault(s.getId(), 0L)));
    }

    /** The startup as its managers (and admins) see it, with its real follower count. */
    private StartupDto manageDto(Startup startup) {
        return startupMapper.toDto(startup, false, true, true, followRepository.countByStartupId(startup.getId()));
    }

    private Map<String, Long> followerCounts(List<String> startupIds) {
        Map<String, Long> counts = new HashMap<>();
        if (startupIds.isEmpty()) return counts;
        for (Object[] row : followRepository.countByStartupIds(startupIds)) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * The sectors that discovery can actually filter by: only sectors some visible startup has, most startups first. Case
     * variants ("AI", "ai") are folded into one entry that shows the spelling most startups use, and the sector filter
     * matches them case-insensitively, so the count and the results agree.
     */
    @Transactional(readOnly = true)
    public List<StartupSectorDto> listSectors(String viewerId) {
        Map<String, Long> totals = new HashMap<>();
        Map<String, String> spelling = new HashMap<>();
        Map<String, Long> spellingCount = new HashMap<>();
        for (Object[] row : startupRepository.countBySector(viewerId != null)) {
            String raw = ((String) row[0]).trim();
            long count = (Long) row[1];
            String key = raw.toLowerCase(java.util.Locale.ROOT);
            totals.merge(key, count, Long::sum);
            if (count > spellingCount.getOrDefault(key, 0L)) {
                spellingCount.put(key, count);
                spelling.put(key, raw);
            }
        }
        return totals.entrySet().stream()
                .map(e -> new StartupSectorDto(spelling.get(e.getKey()), e.getValue()))
                .sorted(java.util.Comparator.comparingLong(StartupSectorDto::count).reversed()
                        .thenComparing(d -> d.sector().toLowerCase(java.util.Locale.ROOT)))
                .limit(50)
                .toList();
    }

    @Transactional
    public StartupDto createStartup(String creatorId, CreateStartupRequest request) {
        Startup startup = Startup.builder()
                .name(request.name().trim())
                .logoUrl(request.logoUrl())
                .tagline(request.tagline())
                .sector(request.sector())
                .problem(request.problem())
                .solution(request.solution())
                .stage(request.stage() == null || request.stage().isBlank() ? StartupStage.IDEA : StartupStage.fromLabel(request.stage()))
                .needs(request.needs() == null ? new java.util.HashSet<>() : new java.util.HashSet<>(request.needs()))
                .chapterId(request.chapterId())
                // The profile fields go through the same normalisation as when they are edited later.
                .location(blankToNull(request.location()))
                .website(normalizeAndValidateUrl(request.website(), "Website"))
                .targetCustomer(blankToNull(request.targetCustomer()))
                .businessModel(blankToNull(request.businessModel()))
                .whatBuilding(blankToNull(request.whatBuilding()))
                .revenue(blankToNull(request.revenue()))
                .customers(blankToNull(request.customers()))
                .users(blankToNull(request.users()))
                .growth(blankToNull(request.growth()))
                .otherTraction(blankToNull(request.otherTraction()))
                .visibility(request.visibility() == null || request.visibility().isBlank() ? StartupVisibility.PUBLIC : parseVisibility(request.visibility()))
                .fundraisingVisible(request.fundraisingVisible() == null || request.fundraisingVisible())
                .build();
        startup = startupRepository.saveAndFlush(startup);

        teamMemberRepository.save(StartupTeamMember.builder()
                .startupId(startup.getId())
                .userId(creatorId)
                .teamRole(StartupTeamMember.TeamRole.FOUNDER)
                .status(StartupTeamMember.Status.ACTIVE)
                .build());

        return manageDto(startup);
    }

    /**
     * An admin adding a startup. With {@code founderEmail}, that member becomes the founder (and is told, since they can
     * now manage it); without it the admin's own account owns the startup. Like every startup it is live straight away.
     */
    @Transactional
    public StartupDto createStartupAsAdmin(String adminId, CreateStartupRequest request, String founderEmail, String ip) {
        String founderId = adminId;
        if (founderEmail != null && !founderEmail.isBlank()) {
            User founder = userRepository.findByEmail(founderEmail.toLowerCase().trim())
                    .orElseThrow(() -> new BadRequestException("No member has that email address"));
            if (founder.getStatus() != AccountStatus.ACTIVE) {
                throw new BadRequestException("That member's account is not active");
            }
            founderId = founder.getId();
        }

        StartupDto created = createStartup(founderId, request);

        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("entityType", "Startup");
        details.put("name", created.name());
        details.put("founderId", founderId);
        auditService.log(adminId, AuditAction.ADMIN_STARTUP_CREATED, "Startup", created.id(), ip, details);

        if (!founderId.equals(adminId)) {
            notificationService.notify(founderId, NotificationType.startup, "A startup was added for you",
                    created.name() + " was added to BuildAdda for you. Open it to add more details.", created.id(), adminId);
        }
        return created;
    }

    @Transactional
    public StartupDto updateStartup(String userId, String startupId, UpdateStartupRequest request) {
        Startup startup = getEntityOrThrow(startupId);
        requireManager(userId, startupId);

        if (request.name() != null) startup.setName(request.name());
        if (request.logoUrl() != null) startup.setLogoUrl(request.logoUrl());
        if (request.location() != null) startup.setLocation(blankToNull(request.location()));
        if (request.website() != null) startup.setWebsite(normalizeAndValidateUrl(request.website(), "Website"));
        if (request.tagline() != null) startup.setTagline(request.tagline());
        if (request.sector() != null) startup.setSector(request.sector());
        if (request.problem() != null) startup.setProblem(request.problem());
        if (request.solution() != null) startup.setSolution(request.solution());
        if (request.targetCustomer() != null) startup.setTargetCustomer(blankToNull(request.targetCustomer()));
        if (request.businessModel() != null) startup.setBusinessModel(blankToNull(request.businessModel()));
        if (request.whatBuilding() != null) startup.setWhatBuilding(blankToNull(request.whatBuilding()));
        if (request.traction() != null) startup.setTraction(request.traction());
        if (request.revenue() != null) startup.setRevenue(blankToNull(request.revenue()));
        if (request.customers() != null) startup.setCustomers(blankToNull(request.customers()));
        if (request.users() != null) startup.setUsers(blankToNull(request.users()));
        if (request.growth() != null) startup.setGrowth(blankToNull(request.growth()));
        if (request.otherTraction() != null) startup.setOtherTraction(blankToNull(request.otherTraction()));
        if (request.keywords() != null) startup.setKeywords(normalizeKeywords(request.keywords()));
        if (request.visibility() != null) startup.setVisibility(parseVisibility(request.visibility()));
        if (request.fundraisingVisible() != null) startup.setFundraisingVisible(request.fundraisingVisible());
        if (request.isRaising() != null) startup.setRaising(request.isRaising());
        if (request.stage() != null) startup.setStage(StartupStage.fromLabel(request.stage()));
        if (request.needs() != null) startup.setNeeds(new java.util.HashSet<>(request.needs()));

        Startup saved = startupRepository.saveAndFlush(startup);
        return manageDto(saved);
    }

    /**
     * Founder-only. The team, follows, updates, roles, materials, fundraise and event links go with the startup. Job
     * postings made for it are people's own content that others may have applied to, so they are never deleted behind
     * the founder's back: while any exist the delete is refused and says why. An idea that became this startup is
     * released back to being a plain idea.
     */
    @Transactional
    public void deleteStartup(String userId, String startupId) {
        Startup startup = getEntityOrThrow(startupId);
        requireFounder(userId, startupId);

        long postings = opportunityRepository.countByStartupId(startupId);
        if (postings > 0) {
            throw new ConflictException("\"" + startup.getName() + "\" still has " + postings + (postings == 1 ? " opportunity" : " opportunities")
                    + " posted for it. " + (postings == 1 ? "It has" : "They have") + " to be deleted by whoever posted "
                    + (postings == 1 ? "it" : "them") + " (Opportunities > Posted by me) before the startup can be deleted.");
        }
        ideaRepository.detachFromStartup(startupId);
        startupRepository.deleteById(startupId);
    }

    @Transactional
    public StartupDto updateLogo(String founderId, String startupId, MultipartFile file) {
        Startup startup = getEntityOrThrow(startupId);
        requireManager(founderId, startupId);
        startup.setLogoUrl(fileStorageService.storeImage(file, "startup-logos"));
        return manageDto(startupRepository.save(startup));
    }

    @Transactional
    public StartupDto removeLogo(String founderId, String startupId) {
        Startup startup = getEntityOrThrow(startupId);
        requireManager(founderId, startupId);
        startup.setLogoUrl(null);
        return manageDto(startupRepository.save(startup));
    }

    /** Same upload as {@link #updateLogo}, minus the "must be a manager of this startup" gate — an admin sets this
     *  right after creating the startup, before deciding (or without ever deciding) who its founder is. Still
     *  refuses a removed startup, same as every other path: restore it first. */
    @Transactional
    public StartupDto updateLogoAsAdmin(String startupId, MultipartFile file) {
        requireNotRemoved(startupId);
        Startup startup = getEntityOrThrow(startupId);
        startup.setLogoUrl(fileStorageService.storeImage(file, "startup-logos"));
        return manageDto(startupRepository.save(startup));
    }

    @Transactional(readOnly = true)
    public List<StartupTeamMemberDto> getMembers(String startupId, String viewerId) {
        accessPolicy.requireReadable(startupId, viewerId);
        return teamMemberRepository.findByStartupIdAndStatus(startupId, StartupTeamMember.Status.ACTIVE).stream()
                .map(m -> startupMapper.toDto(m, memberProfile(m.getUserId(), viewerId)))
                .toList();
    }

    /** A team member as the viewer may see them (privacy and blocks apply); null when they can't be shown, so the list still loads. */
    private UserDto memberProfile(String userId, String viewerId) {
        try {
            return userService.getUser(userId, viewerId);
        } catch (ResourceNotFoundException | ForbiddenException e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public StartupTeamMemberDto getMyMembership(String userId, String startupId) {
        accessPolicy.requireReadable(startupId, userId);
        return teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .map(startupMapper::toDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<StartupJoinRequestDto> getJoinRequests(String founderId, String startupId) {
        Startup startup = getEntityOrThrow(startupId);
        requireManager(founderId, startupId);
        return teamMemberRepository.findByStartupIdAndStatus(startupId, StartupTeamMember.Status.PENDING).stream()
                .map(m -> toJoinRequestDto(m, startup, founderId))
                .toList();
    }

    private StartupJoinRequestDto toJoinRequestDto(StartupTeamMember member, Startup startup, String viewerId) {
        UserDto applicant = userService.getUser(member.getUserId(), viewerId);
        String roleTitle = member.getRoleId() == null ? null
                : roleRepository.findById(member.getRoleId()).map(StartupRole::getTitle).orElse(null);
        return new StartupJoinRequestDto(
                member.getId(),
                startup.getId(),
                startup.getName(),
                applicant,
                member.getStatus().name(),
                member.getRoleId(),
                roleTitle,
                member.getMessage(),
                member.getCreatedAt(),
                member.getReviewedAt()
        );
    }

    @Transactional
    public StartupTeamMemberDto requestToJoin(String userId, String startupId, String roleId, String message) {
        Startup startup = accessPolicy.requireReadable(startupId, userId);
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (roleId != null && !roleRepository.findById(roleId).map(r -> r.getStartupId().equals(startupId)).orElse(false)) {
            throw new BadRequestException("Invalid role for this startup");
        }

        StartupTeamMember member = teamMemberRepository.findByStartupIdAndUserId(startupId, userId).orElse(null);
        if (member != null) {
            if (member.getStatus() == StartupTeamMember.Status.ACTIVE) {
                throw new ConflictException("Already a member of this startup");
            }
            if (member.getStatus() == StartupTeamMember.Status.PENDING) {
                throw new ConflictException("You already have a pending request to join this startup");
            }
            // Previously REJECTED — allow a fresh request, reusing the row.
            member.setStatus(StartupTeamMember.Status.PENDING);
            member.setRoleId(roleId);
            member.setMessage(message);
            member.setReviewedAt(null);
        } else {
            member = StartupTeamMember.builder()
                    .startupId(startupId)
                    .userId(userId)
                    .status(StartupTeamMember.Status.PENDING)
                    .roleId(roleId)
                    .message(message)
                    .build();
        }
        member = teamMemberRepository.saveAndFlush(member);

        teamMemberRepository.findByStartupIdAndTeamRoleIn(startupId, MANAGER_ROLES).forEach(manager ->
                notificationService.notify(manager.getUserId(), NotificationType.startup,
                        "New join request", "Someone wants to join " + startup.getName(), startupId, userId));

        return startupMapper.toDto(member);
    }

    @Transactional
    public StartupTeamMemberDto acceptJoinRequest(String founderId, String memberId) {
        return transitionJoinRequest(founderId, memberId, StartupTeamMember.Status.ACTIVE,
                "You're on the team", "%s accepted your request to join");
    }

    @Transactional
    public StartupTeamMemberDto rejectJoinRequest(String founderId, String memberId) {
        return transitionJoinRequest(founderId, memberId, StartupTeamMember.Status.REJECTED,
                "Request declined", "%s declined your request to join");
    }

    private StartupTeamMemberDto transitionJoinRequest(String founderId, String memberId,
                                                         StartupTeamMember.Status newStatus,
                                                         String notificationTitle, String messageTemplate) {
        StartupTeamMember member = teamMemberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found: " + memberId));
        Startup startup = getEntityOrThrow(member.getStartupId());
        requireManager(founderId, startup.getId());

        if (member.getStatus() != StartupTeamMember.Status.PENDING) {
            throw new BadRequestException("This request has already been decided");
        }

        member.setStatus(newStatus);
        member.setReviewedAt(Instant.now());
        member = teamMemberRepository.saveAndFlush(member);

        notificationService.notify(member.getUserId(), NotificationType.startup,
                notificationTitle, String.format(messageTemplate, startup.getName()), startup.getId(), founderId);

        return startupMapper.toDto(member);
    }

    @Transactional
    public void leaveTeam(String userId, String startupId) {
        getEntityOrThrow(startupId);
        StartupTeamMember member = teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .filter(m -> m.getStatus() == StartupTeamMember.Status.ACTIVE)
                .orElseThrow(() -> new BadRequestException("You are not a member of this startup"));
        if (member.isFounder()) {
            throw new BadRequestException("Founders can't leave their own startup");
        }
        teamMemberRepository.delete(member);
    }

    @Transactional
    public StartupTeamMemberDto addMember(String actingUserId, String startupId, String userId, String roleId, String teamRoleLabel) {
        Startup startup = getEntityOrThrow(startupId);
        requireManager(actingUserId, startupId);
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (roleId != null && !roleRepository.findById(roleId).map(r -> r.getStartupId().equals(startupId)).orElse(false)) {
            throw new BadRequestException("Invalid role for this startup");
        }

        // Only a founder can grant Admin at add-time — an Admin adding a teammate can only add them as a Member.
        StartupTeamMember.TeamRole teamRole = StartupTeamMember.TeamRole.MEMBER;
        if (teamRoleLabel != null && !teamRoleLabel.isBlank()) {
            teamRole = parseAssignableTeamRole(teamRoleLabel);
            if (teamRole == StartupTeamMember.TeamRole.ADMIN && !isFounderMember(actingUserId, startupId)) {
                throw new ForbiddenException("Only a founder can grant the Admin role");
            }
        }

        StartupTeamMember member = teamMemberRepository.findByStartupIdAndUserId(startupId, userId).orElse(null);
        if (member != null && member.getStatus() == StartupTeamMember.Status.ACTIVE) {
            throw new ConflictException("Already a member of this startup");
        }
        if (member != null) {
            member.setStatus(StartupTeamMember.Status.ACTIVE);
            member.setRoleId(roleId);
            member.setTeamRole(teamRole);
            member.setReviewedAt(Instant.now());
        } else {
            member = StartupTeamMember.builder()
                    .startupId(startupId)
                    .userId(userId)
                    .status(StartupTeamMember.Status.ACTIVE)
                    .roleId(roleId)
                    .teamRole(teamRole)
                    .reviewedAt(Instant.now())
                    .build();
        }
        member = teamMemberRepository.saveAndFlush(member);

        notificationService.notify(userId, NotificationType.startup,
                "You're on the team", "You were added to the team for " + startup.getName(), startupId, actingUserId);

        return startupMapper.toDto(member);
    }

    @Transactional
    public StartupTeamMemberDto updateMemberRole(String founderId, String startupId, String userId, String teamRoleLabel) {
        getEntityOrThrow(startupId);
        requireFounder(founderId, startupId);
        StartupTeamMember.TeamRole newRole = parseAssignableTeamRole(teamRoleLabel);

        StartupTeamMember member = teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .filter(m -> m.getStatus() == StartupTeamMember.Status.ACTIVE)
                .orElseThrow(() -> new BadRequestException("This user is not a member of this startup"));
        if (member.isFounder()) {
            throw new BadRequestException("The founder's role can't be changed");
        }

        member.setTeamRole(newRole);
        member = teamMemberRepository.saveAndFlush(member);

        notificationService.notify(userId, NotificationType.startup,
                "Your role was updated", "You're now " + (newRole == StartupTeamMember.TeamRole.ADMIN ? "an Admin" : "a Member")
                        + " on the team", startupId, founderId);

        return startupMapper.toDto(member);
    }

    /** Admin/Member are the only roles ever assignable through the API — Founder is set once at creation/conversion and never granted. */
    private StartupTeamMember.TeamRole parseAssignableTeamRole(String label) {
        try {
            StartupTeamMember.TeamRole role = StartupTeamMember.TeamRole.valueOf(label.trim().toUpperCase());
            if (role == StartupTeamMember.TeamRole.FOUNDER) {
                throw new BadRequestException("The Founder role can't be assigned");
            }
            return role;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown team role: " + label);
        }
    }

    @Transactional
    public void removeMember(String actingUserId, String startupId, String userId) {
        Startup startup = getEntityOrThrow(startupId);
        requireManager(actingUserId, startupId);
        if (userId.equals(actingUserId)) {
            throw new BadRequestException("Use leave team to remove yourself");
        }
        StartupTeamMember member = teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .filter(m -> m.getStatus() == StartupTeamMember.Status.ACTIVE)
                .orElseThrow(() -> new BadRequestException("This user is not a member of this startup"));
        if (member.isFounder()) {
            throw new BadRequestException("Founders can't be removed");
        }
        if (member.isAdmin() && !isFounderMember(actingUserId, startupId)) {
            throw new ForbiddenException("Only a founder can remove an admin");
        }
        teamMemberRepository.delete(member);

        notificationService.notify(userId, NotificationType.startup,
                "Removed from the team", "You were removed from the team for " + startup.getName(), startupId, actingUserId);
    }

    public record FollowResult(boolean following) {}

    @Transactional
    public FollowResult toggleFollow(String userId, String startupId) {
        accessPolicy.requireReadable(startupId, userId);
        if (followRepository.existsByUserIdAndStartupId(userId, startupId)) {
            followRepository.deleteByUserIdAndStartupId(userId, startupId);
            return new FollowResult(false);
        } else {
            followRepository.save(StartupFollow.builder().userId(userId).startupId(startupId).build());
            return new FollowResult(true);
        }
    }

    @Transactional
    public StartupUpdateDto postUpdate(String userId, String startupId, String content) {
        getEntityOrThrow(startupId);
        requireTeamMember(userId, startupId);
        StartupUpdate update = updateRepository.saveAndFlush(StartupUpdate.builder().startupId(startupId).content(content).build());
        return startupMapper.toDto(update);
    }

    @Transactional(readOnly = true)
    public List<StartupUpdateDto> getUpdates(String startupId, String viewerId) {
        accessPolicy.requireReadable(startupId, viewerId);
        return updateRepository.findByStartupIdOrderByCreatedAtDesc(startupId, PageRequest.of(0, 100))
                .map(startupMapper::toDto)
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<StartupRoleDto> getRoles(String startupId, String viewerId) {
        accessPolicy.requireReadable(startupId, viewerId);
        return roleRepository.findByStartupId(startupId).stream().map(startupMapper::toDto).toList();
    }

    @Transactional
    public StartupRoleDto createRole(String userId, String startupId, CreateStartupRoleRequest request) {
        getEntityOrThrow(startupId);
        requireManager(userId, startupId);
        StartupRole role = roleRepository.saveAndFlush(StartupRole.builder()
                .startupId(startupId)
                .title(request.title())
                .type(StartupRoleType.fromLabel(request.type()))
                .location(request.location())
                .remote(request.remote())
                .build());
        return startupMapper.toDto(role);
    }

    // ---- Startup materials ----

    /** How long a minted material URL stays valid — same reasoning and value as FeedService's own
     *  ATTACHMENT_URL_TTL (which mirrors ConversationService's, the original source of this pattern). */
    private static final Duration MATERIAL_URL_TTL = Duration.ofHours(6);

    @Transactional(readOnly = true)
    public List<StartupMaterialDto> getMaterials(String startupId, String viewerId) {
        accessPolicy.requireReadable(startupId, viewerId);
        boolean canManage = canManageStartup(viewerId, startupId);
        return materialRepository.findByStartupIdOrderBySortOrderAscCreatedAtAsc(startupId).stream()
                .map(m -> resolveMaterialUrl(startupMapper.toDto(m, canManage)))
                .toList();
    }

    /** {@code dto.url()} is either a real external link (materials whose type {@link
     *  StartupMaterialType#isExternalLink()}, e.g. Website — never our storage, passed through as-is),
     *  a legacy full public URL (from before uploaded materials were made private — left exactly as
     *  stored, see FeedService#resolveAttachmentUrl for why), or the private key an upload since minted,
     *  which needs a fresh presigned URL on every read. */
    private StartupMaterialDto resolveMaterialUrl(StartupMaterialDto dto) {
        if (fileStorageService.isHostedUrl(dto.url())) return dto;
        boolean isExternalLink;
        try {
            isExternalLink = StartupMaterialType.fromLabel(dto.materialType()).isExternalLink();
        } catch (IllegalArgumentException e) {
            isExternalLink = false;
        }
        if (isExternalLink) return dto;
        String presigned = fileStorageService.presignGet(dto.url(), MATERIAL_URL_TTL);
        return new StartupMaterialDto(dto.id(), dto.startupId(), dto.materialType(), dto.title(), presigned,
                dto.originalFileName(), dto.contentType(), dto.sortOrder(), dto.canManage(), dto.createdAt(), dto.updatedAt());
    }

    @Transactional
    public StartupMaterialDto addMaterial(String userId, String startupId, String materialTypeLabel,
                                           String title, String url, MultipartFile file) {
        getEntityOrThrow(startupId);
        requireManager(userId, startupId);
        StartupMaterialType type = parseMaterialType(materialTypeLabel);

        StartupMaterial.StartupMaterialBuilder builder = StartupMaterial.builder()
                .startupId(startupId)
                .materialType(type)
                .title(blankToNull(title))
                .createdByUserId(userId);

        if (type.isExternalLink()) {
            if (file != null && !file.isEmpty()) {
                throw new BadRequestException(type.getLabel() + " is a link, not a file — provide a URL");
            }
            if (url == null || url.isBlank()) {
                throw new BadRequestException(type.getLabel() + " requires a URL");
            }
            builder.url(normalizeAndValidateUrl(url, type.getLabel()));
        } else {
            if (url != null && !url.isBlank()) {
                throw new BadRequestException(type.getLabel() + " must be an uploaded file, not a URL");
            }
            if (file == null || file.isEmpty()) {
                throw new BadRequestException(type.getLabel() + " requires a file to upload");
            }
            FileStorageService.StoredPrivateMedia media = fileStorageService.storePrivateMedia(file, "startup-materials");
            requireExpectedKind(type, media.kind());
            builder.url(media.key())
                    .originalFileName(file.getOriginalFilename())
                    .contentType(file.getContentType());
        }

        StartupMaterial saved = materialRepository.saveAndFlush(builder.build());
        return resolveMaterialUrl(startupMapper.toDto(saved, true));
    }

    @Transactional
    public StartupMaterialDto updateMaterial(String userId, String materialId, String title, String url, MultipartFile file) {
        StartupMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + materialId));
        requireManager(userId, material.getStartupId());

        if (title != null) material.setTitle(blankToNull(title));

        if (material.getMaterialType().isExternalLink()) {
            if (url != null) {
                if (url.isBlank()) throw new BadRequestException("URL cannot be blank");
                material.setUrl(normalizeAndValidateUrl(url, material.getMaterialType().getLabel()));
            }
        } else if (file != null && !file.isEmpty()) {
            FileStorageService.StoredPrivateMedia media = fileStorageService.storePrivateMedia(file, "startup-materials");
            requireExpectedKind(material.getMaterialType(), media.kind());
            deleteStoredMaterialFile(material.getUrl());
            material.setUrl(media.key());
            material.setOriginalFileName(file.getOriginalFilename());
            material.setContentType(file.getContentType());
        }

        return resolveMaterialUrl(startupMapper.toDto(materialRepository.saveAndFlush(material), true));
    }

    @Transactional
    public void deleteMaterial(String userId, String materialId) {
        StartupMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + materialId));
        requireManager(userId, material.getStartupId());
        deleteStoredMaterialFile(material.getUrl());
        materialRepository.delete(material);
    }

    /** {@code stored} is null/blank for an external-link material (nothing of ours to delete), a
     *  legacy full public URL (deleteIfHosted), or a private key from an upload since materials became
     *  private (deleteByKey) — see resolveMaterialUrl for the same three-way distinction on read. */
    private void deleteStoredMaterialFile(String stored) {
        if (stored == null || stored.isBlank()) return;
        if (fileStorageService.isHostedUrl(stored)) {
            fileStorageService.deleteIfHosted(stored);
        } else if (!stored.matches("(?i)^https?://.*")) {
            fileStorageService.deleteByKey(stored);
        }
    }

    private StartupMaterialType parseMaterialType(String label) {
        try {
            return StartupMaterialType.fromLabel(label);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown material type: " + label);
        }
    }

    /** Keeps uploads honest about what each category is for, without loosening the underlying
     *  image/video/PDF whitelist already enforced by FileStorageService. */
    private void requireExpectedKind(StartupMaterialType type, FileStorageService.AttachmentKind kind) {
        boolean ok = switch (type) {
            case SCREENSHOTS -> kind == FileStorageService.AttachmentKind.IMAGE;
            case PITCH_DECK, OTHER_DOCUMENT -> kind == FileStorageService.AttachmentKind.PDF;
            case PRODUCT_DEMO -> kind == FileStorageService.AttachmentKind.IMAGE || kind == FileStorageService.AttachmentKind.VIDEO;
            default -> true;
        };
        if (!ok) {
            String expected = switch (type) {
                case SCREENSHOTS -> "an image";
                case PITCH_DECK, OTHER_DOCUMENT -> "a PDF";
                case PRODUCT_DEMO -> "an image or video";
                default -> "a supported file";
            };
            throw new BadRequestException(type.getLabel() + " must be " + expected);
        }
    }

    // ---- authorization / visibility helpers ----

    /** An admin-removed startup looks nonexistent to everyone, so nobody (its own team included) can change it. */
    private void requireNotRemoved(String startupId) {
        if (startupRepository.findById(startupId).map(Startup::isRemovedByAdmin).orElse(false)) {
            throw new ResourceNotFoundException("Startup not found: " + startupId);
        }
    }

    private void requireFounder(String userId, String startupId) {
        requireNotRemoved(startupId);
        if (!isFounderMember(userId, startupId)) {
            throw new ForbiddenException("Only a founder of this startup can perform this action");
        }
    }

    private boolean isFounderMember(String userId, String startupId) {
        if (userId == null) return false;
        return teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .map(m -> m.isFounder() && m.getStatus() == StartupTeamMember.Status.ACTIVE)
                .orElse(false);
    }

    /** Founder or Admin — gates edit-startup/manage-team/post-jobs/edit-fundraising. Delete stays founder-only via requireFounder. */
    private void requireManager(String userId, String startupId) {
        requireNotRemoved(startupId);
        if (!canManageStartup(userId, startupId)) {
            throw new ForbiddenException("Only a founder or admin of this startup can perform this action");
        }
    }

    private boolean canManageStartup(String userId, String startupId) {
        if (userId == null) return false;
        return teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .map(m -> m.canManage() && m.getStatus() == StartupTeamMember.Status.ACTIVE)
                .orElse(false);
    }

    private void requireTeamMember(String userId, String startupId) {
        requireNotRemoved(startupId);
        boolean isMember = teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .map(m -> m.getStatus() == StartupTeamMember.Status.ACTIVE)
                .orElse(false);
        if (!isMember) throw new ForbiddenException("Only a team member of this startup can perform this action");
    }

    private boolean isActiveTeamMember(String userId, String startupId) {
        if (userId == null) return false;
        return teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .map(m -> m.getStatus() == StartupTeamMember.Status.ACTIVE)
                .orElse(false);
    }

    /** Fundraising data is visible to the founder/team of the startup regardless of the toggle,
     *  and to everyone else only when the founder has switched fundraising visibility on. */
    private boolean canViewFundraising(Startup startup, String viewerId) {
        return startup.isFundraisingVisible() || isActiveTeamMember(viewerId, startup.getId());
    }

    private StartupVisibility parseVisibility(String label) {
        try {
            return StartupVisibility.fromLabel(label);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown visibility: " + label);
        }
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private String normalizeKeywords(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String joined = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        return joined.isBlank() ? null : joined;
    }

    /** Mirrors ResourceService's scheme-optional normalization, plus rejects strings that still
     *  don't parse as a URL afterwards (e.g. containing spaces or no host at all). */
    private String normalizeAndValidateUrl(String rawUrl, String fieldLabel) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isEmpty()) return null;
        String candidate = trimmed.matches("(?i)^https?://.*") ? trimmed : "https://" + trimmed;
        try {
            URI uri = new URI(candidate);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BadRequestException(fieldLabel + " is not a valid URL");
            }
        } catch (URISyntaxException e) {
            throw new BadRequestException(fieldLabel + " is not a valid URL");
        }
        return candidate;
    }
}
