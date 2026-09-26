package com.nukkad.idea.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.common.paging.PageRequests;
import com.nukkad.common.publishing.PlatformAuthorResolver;
import com.nukkad.common.publishing.PublisherIdentity;
import com.nukkad.idea.dto.ConvertToStartupRequest;
import com.nukkad.idea.dto.ExpressInterestRequest;
import com.nukkad.idea.dto.IdeaDto;
import com.nukkad.idea.dto.IdeaInterestDto;
import com.nukkad.idea.dto.PostIdeaRequest;
import com.nukkad.idea.dto.UpdateIdeaRequest;
import com.nukkad.idea.entity.ContributionArea;
import com.nukkad.idea.entity.Idea;
import com.nukkad.idea.entity.IdeaInterest;
import com.nukkad.idea.entity.IdeaInterestStatus;
import com.nukkad.idea.entity.IdeaStage;
import com.nukkad.idea.mapper.IdeaMapper;
import com.nukkad.idea.repository.IdeaIdCount;
import com.nukkad.idea.repository.IdeaInterestRepository;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.idea.repository.IdeaSpecifications;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.startup.dto.StartupDto;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.mapper.StartupMapper;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import com.nukkad.user.dto.ExperienceDto;
import com.nukkad.user.dto.ProjectDto;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.User;
import com.nukkad.user.entity.UserExperience;
import com.nukkad.user.entity.UserProject;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.UserExperienceRepository;
import com.nukkad.user.repository.UserProjectRepository;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final IdeaInterestRepository ideaInterestRepository;
    private final UserRepository userRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserProjectRepository userProjectRepository;
    private final StartupRepository startupRepository;
    private final StartupTeamMemberRepository startupTeamMemberRepository;
    private final IdeaMapper ideaMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final StartupMapper startupMapper;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final PlatformAuthorResolver platformAuthorResolver;

    public IdeaService(IdeaRepository ideaRepository,
                        IdeaInterestRepository ideaInterestRepository,
                        UserRepository userRepository,
                        UserExperienceRepository userExperienceRepository,
                        UserProjectRepository userProjectRepository,
                        StartupRepository startupRepository,
                        StartupTeamMemberRepository startupTeamMemberRepository,
                        IdeaMapper ideaMapper,
                        UserMapper userMapper,
                        UserService userService,
                        StartupMapper startupMapper,
                        NotificationService notificationService,
                        AuditService auditService,
                        PlatformAuthorResolver platformAuthorResolver) {
        this.ideaRepository = ideaRepository;
        this.ideaInterestRepository = ideaInterestRepository;
        this.userRepository = userRepository;
        this.userExperienceRepository = userExperienceRepository;
        this.userProjectRepository = userProjectRepository;
        this.startupRepository = startupRepository;
        this.startupTeamMemberRepository = startupTeamMemberRepository;
        this.ideaMapper = ideaMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.startupMapper = startupMapper;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.platformAuthorResolver = platformAuthorResolver;
    }

    public Idea getEntityOrThrow(String id) {
        return ideaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Idea not found: " + id));
    }

    private IdeaDto toIdeaDto(Idea idea) {
        return ideaMapper.toDto(idea, (int) ideaInterestRepository.countByIdeaIdAndStatusNotIn(
                idea.getId(), List.of(IdeaInterestStatus.WITHDRAWN, IdeaInterestStatus.REJECTED)));
    }

    /** The same interest count as {@link #toIdeaDto}, but for a whole page at once: one grouped-count query
     *  instead of one {@link #toIdeaDto} query per row. */
    private Function<Idea, IdeaDto> batchIdeaDtoMapper(List<Idea> ideas) {
        List<String> ideaIds = ideas.stream().map(Idea::getId).toList();
        if (ideaIds.isEmpty()) {
            return this::toIdeaDto;
        }
        Map<String, Long> interestCounts = ideaInterestRepository.countGroupedByIdeaIdInAndStatusNotIn(
                        ideaIds, List.of(IdeaInterestStatus.WITHDRAWN, IdeaInterestStatus.REJECTED)).stream()
                .collect(Collectors.toMap(IdeaIdCount::getIdeaId, IdeaIdCount::getTotal));
        return idea -> ideaMapper.toDto(idea, interestCounts.getOrDefault(idea.getId(), 0L).intValue());
    }

    @Transactional(readOnly = true)
    public IdeaDto getIdea(String id, String viewerId) {
        Idea idea = getEntityOrThrow(id);
        if (idea.isRemovedByAdmin()) {
            throw new ResourceNotFoundException("Idea not found: " + id);
        }
        // Pre-publish gate: a PENDING/REJECTED idea is only visible to its own creator (so they
        // can see their own submission's review status) or an admin (via getIdeaForAdmin below).
        if (idea.getModerationStatus() != ModerationStatus.APPROVED && !idea.getCreatorId().equals(viewerId)) {
            throw new ResourceNotFoundException("Idea not found: " + id);
        }
        return toIdeaDto(idea);
    }

    // Admin-only: bypasses the removed-by-admin check above so a removed idea can still be
    // reviewed (and restored) from the admin panel instead of 404ing for the reviewer too.
    @Transactional(readOnly = true)
    public IdeaDto getIdeaForAdmin(String id) {
        return toIdeaDto(getEntityOrThrow(id));
    }

    // PUBLIC listing — always excludes removed content; excludes non-approved content unless the
    // caller is explicitly filtering to their own ideas (creatorId == viewerId). PersonProfilePage
    // reuses this same endpoint (not a separate "my ideas" endpoint) to show a user's own
    // pending/rejected submissions on their own profile, so that case must stay visible to them.
    @Transactional(readOnly = true)
    public Page<IdeaDto> listIdeas(String q, String stage, String category, String helpNeeded,
                                    String chapterId, String creatorId, String viewerId, int page, int size) {
        boolean ownContent = creatorId != null && creatorId.equals(viewerId);
        Specification<Idea> spec = IdeaSpecifications.combine(
                IdeaSpecifications.search(q),
                IdeaSpecifications.stage(stage),
                IdeaSpecifications.category(category),
                IdeaSpecifications.helpNeeded(helpNeeded),
                IdeaSpecifications.chapterId(chapterId),
                IdeaSpecifications.creatorId(creatorId),
                IdeaSpecifications.notRemoved(),
                ownContent ? null : IdeaSpecifications.approved()
        );
        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Idea> results = ideaRepository.findAll(spec, pageable);
        return results.map(batchIdeaDtoMapper(results.getContent()));
    }

    // ADMIN-ONLY — never applies the approved() gate; an admin must see PENDING/REJECTED ideas to
    // review them. includeRemoved and moderationStatus are independent, optional narrowing filters.
    @Transactional(readOnly = true)
    public Page<IdeaDto> listIdeasForAdmin(String q, String stage, String category, String helpNeeded, String chapterId,
                                            String creatorId, boolean includeRemoved, ModerationStatus moderationStatus,
                                            int page, int size) {
        Specification<Idea> spec = IdeaSpecifications.combine(
                IdeaSpecifications.search(q),
                IdeaSpecifications.stage(stage),
                IdeaSpecifications.category(category),
                IdeaSpecifications.helpNeeded(helpNeeded),
                IdeaSpecifications.chapterId(chapterId),
                IdeaSpecifications.creatorId(creatorId),
                includeRemoved ? null : IdeaSpecifications.notRemoved(),
                IdeaSpecifications.moderationStatus(moderationStatus)
        );
        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Idea> results = ideaRepository.findAll(spec, pageable);
        return results.map(batchIdeaDtoMapper(results.getContent()));
    }

    // Admin-only moderation toggle: hides an idea from public discovery (and 404s its public detail
    // page) without deleting it, or reverses that. Reused across the three content types this same
    // way rather than inventing a per-type moderation model — see Startup/OpportunityService.
    @Transactional
    public IdeaDto setRemovedByAdmin(String adminId, String ideaId, boolean removed, String reason, String ip) {
        Idea idea = getEntityOrThrow(ideaId);
        idea.setRemovedByAdmin(removed);
        idea.setRemovalReason(removed ? reason : null);
        idea = ideaRepository.saveAndFlush(idea);

        Map<String, Object> details = new HashMap<>();
        details.put("entityType", "Idea");
        if (removed && reason != null && !reason.isBlank()) details.put("reason", reason);
        auditService.log(adminId, removed ? AuditAction.ADMIN_CONTENT_REMOVED : AuditAction.ADMIN_CONTENT_RESTORED,
                "Idea", ideaId, ip, details);

        return toIdeaDto(idea);
    }

    // Pre-publish approval gate: an idea may be reviewed exactly once (PENDING -> APPROVED/REJECTED)
    // — see ReportService.resolve for the identical "reviewed once" rationale. A rejection reason is
    // required so the creator understands why; an approval note is optional (rarely used).
    @Transactional
    public IdeaDto reviewModeration(String adminId, String ideaId, boolean approved, String reason, String ip) {
        Idea idea = getEntityOrThrow(ideaId);
        if (idea.getModerationStatus() != ModerationStatus.PENDING) {
            throw new ConflictException("This idea has already been reviewed");
        }
        if (!approved && (reason == null || reason.isBlank())) {
            throw new BadRequestException("A reason is required when rejecting an idea");
        }

        idea.setModerationStatus(approved ? ModerationStatus.APPROVED : ModerationStatus.REJECTED);
        idea.setRejectionReason(approved ? null : reason);
        idea.setModerationReviewedBy(adminId);
        idea.setModerationReviewedAt(Instant.now());
        idea = ideaRepository.saveAndFlush(idea);

        auditService.log(adminId, approved ? AuditAction.ADMIN_CONTENT_APPROVED : AuditAction.ADMIN_CONTENT_REJECTED,
                "Idea", ideaId, ip, approved ? Map.of() : Map.of("reason", reason));

        notificationService.notify(idea.getCreatorId(), NotificationType.idea_interest,
                approved ? "Your idea was approved" : "Your idea was not approved",
                approved ? "\"" + idea.getTitle() + "\" is now visible to the community."
                        : "\"" + idea.getTitle() + "\" was not approved: " + reason,
                ideaId, adminId);

        return toIdeaDto(idea);
    }

    @Transactional
    public IdeaDto postIdea(String creatorId, PostIdeaRequest request) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + creatorId));

        Idea idea = Idea.builder()
                .title(request.title().trim())
                .problem(request.problem())
                .solution(request.solution())
                .targetCustomer(request.targetCustomer())
                .stage(resolveStage(request.stage()))
                .category(request.category())
                .creatorId(creatorId)
                .chapterId(creator.getChapterId())
                .tags(request.tags() == null ? new HashSet<>() : new HashSet<>(request.tags()))
                .helpNeeded(resolveHelpNeeded(request.helpNeeded()))
                .teamMemberIds(new HashSet<>(Set.of(creatorId)))
                .build();

        // saveAndFlush (not save): id/@CreationTimestamp are only assigned once the INSERT
        // actually runs, and the audit log + response DTO both need those values immediately.
        idea = ideaRepository.saveAndFlush(idea);
        auditService.log(creatorId, AuditAction.CREATE_IDEA, "Idea", idea.getId(), null);
        return toIdeaDto(idea);
    }

    /**
     * An admin publishing an idea from the admin panel. With {@code creatorEmail}, that member is
     * attributed as its creator (and is told, since it now appears as theirs); without it the admin's
     * own account is, and the idea is treated as unattributed platform content — {@code publisherIdentityRaw}
     * picks which identity to show for it instead of that admin's real name (same idea as Post — see
     * FeedService#createAsAdmin). Unlike a member's own idea, this is live immediately (APPROVED)
     * rather than entering the pending-moderation queue.
     */
    @Transactional
    public IdeaDto createIdeaAsAdmin(String adminId, PostIdeaRequest request, String creatorEmail,
                                       String publisherIdentityRaw, String ip) {
        String creatorId = platformAuthorResolver.resolve(adminId, creatorEmail);
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + creatorId));
        boolean postedAsPlatform = creatorId.equals(adminId);
        PublisherIdentity publisherIdentity = postedAsPlatform
                ? PublisherIdentity.parse(publisherIdentityRaw, PublisherIdentity.BUILDADDA)
                : PublisherIdentity.BUILDADDA;

        Idea idea = Idea.builder()
                .title(request.title().trim())
                .problem(request.problem())
                .solution(request.solution())
                .targetCustomer(request.targetCustomer())
                .stage(resolveStage(request.stage()))
                .category(request.category())
                .creatorId(creatorId)
                .postedAsPlatform(postedAsPlatform)
                .publisherIdentity(publisherIdentity)
                .chapterId(creator.getChapterId())
                .tags(request.tags() == null ? new HashSet<>() : new HashSet<>(request.tags()))
                .helpNeeded(resolveHelpNeeded(request.helpNeeded()))
                .teamMemberIds(new HashSet<>(Set.of(creatorId)))
                .moderationStatus(ModerationStatus.APPROVED)
                .build();

        idea = ideaRepository.saveAndFlush(idea);
        auditService.log(adminId, AuditAction.ADMIN_IDEA_CREATED, "Idea", idea.getId(), ip, Map.of());

        if (!creatorId.equals(adminId)) {
            notificationService.notify(creatorId, NotificationType.idea, "An idea was added for you",
                    "\"" + idea.getTitle() + "\" was added to BuildAdda for you.", idea.getId(), adminId);
        }
        return toIdeaDto(idea);
    }

    @Transactional
    public IdeaDto updateIdea(String userId, String ideaId, UpdateIdeaRequest request) {
        Idea idea = getEntityOrThrow(ideaId);
        requireCreator(userId, idea);

        if (request.title() != null) idea.setTitle(request.title());
        if (request.problem() != null) idea.setProblem(request.problem());
        if (request.solution() != null) idea.setSolution(request.solution());
        if (request.targetCustomer() != null) idea.setTargetCustomer(request.targetCustomer());
        if (request.category() != null) idea.setCategory(request.category());
        if (request.stage() != null) idea.setStage(IdeaStage.fromLabel(request.stage()));
        if (request.tags() != null) idea.setTags(new HashSet<>(request.tags()));
        if (request.helpNeeded() != null) idea.setHelpNeeded(resolveHelpNeeded(request.helpNeeded()));

        auditService.log(userId, AuditAction.UPDATE_IDEA, "Idea", idea.getId(), null);
        return toIdeaDto(ideaRepository.saveAndFlush(idea));
    }

    @Transactional
    public void deleteIdea(String userId, String ideaId) {
        Idea idea = getEntityOrThrow(ideaId);
        requireCreator(userId, idea);
        if (idea.getStartupId() != null) {
            throw new ConflictException("Cannot delete an idea that has already been converted into a startup");
        }
        ideaInterestRepository.findByIdeaId(ideaId).forEach(i -> ideaInterestRepository.deleteById(i.getId()));
        ideaRepository.delete(idea);
        auditService.log(userId, AuditAction.DELETE_IDEA, "Idea", ideaId, null);
    }

    @Transactional
    public IdeaInterestDto expressInterest(String userId, String ideaId, ExpressInterestRequest request) {
        Idea idea = getEntityOrThrow(ideaId);
        if (idea.getStartupId() != null) {
            throw new BadRequestException("This idea has already become a startup and is no longer looking for team members");
        }
        if (idea.getCreatorId().equals(userId)) {
            throw new BadRequestException("You cannot express interest in your own idea");
        }
        User applicantUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        var existing = ideaInterestRepository.findByIdeaIdAndUserId(ideaId, userId);
        if (existing.isPresent() && existing.get().getStatus() != IdeaInterestStatus.WITHDRAWN) {
            throw new BadRequestException("You've already expressed interest in this idea");
        }

        Set<String> skills = (request.relevantSkills() == null || request.relevantSkills().isEmpty())
                ? new LinkedHashSet<>(applicantUser.getSkills())
                : new LinkedHashSet<>(request.relevantSkills());

        List<String> experienceIds = validateOwnedExperienceIds(userId, request.experienceIds());
        List<String> projectIds = validateOwnedProjectIds(userId, request.projectIds());

        IdeaInterest interest = existing.orElseGet(() -> IdeaInterest.builder().ideaId(ideaId).userId(userId).build());
        interest.setStatus(IdeaInterestStatus.PENDING);
        interest.setContributionAreas(resolveHelpNeeded(request.contributionAreas()));
        interest.setMessage(request.message());
        interest.setRelevantSkills(new ArrayList<>(skills));
        interest.setExperienceIds(experienceIds);
        interest.setProjectIds(projectIds);
        interest.setReviewedAt(null);
        interest = ideaInterestRepository.saveAndFlush(interest);

        notificationService.notify(idea.getCreatorId(), NotificationType.idea_interest,
                "New interest in your idea", applicantUser.getName() + " is interested in \"" + idea.getTitle() + "\"",
                ideaId, userId);

        return toInterestDto(interest, idea, userId);
    }

    private List<String> validateOwnedExperienceIds(String userId, List<String> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) return new ArrayList<>();
        Set<String> owned = userExperienceRepository.findByUser_IdOrderBySortOrderAsc(userId).stream()
                .map(UserExperience::getId).collect(Collectors.toSet());
        return requestedIds.stream().distinct().filter(owned::contains).toList();
    }

    private List<String> validateOwnedProjectIds(String userId, List<String> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) return new ArrayList<>();
        Set<String> owned = userProjectRepository.findByUser_IdOrderBySortOrderAsc(userId).stream()
                .map(UserProject::getId).collect(Collectors.toSet());
        return requestedIds.stream().distinct().filter(owned::contains).toList();
    }

    @Transactional
    public void withdrawInterest(String userId, String ideaId) {
        Idea idea = getEntityOrThrow(ideaId);
        IdeaInterest interest = ideaInterestRepository.findByIdeaIdAndUserId(ideaId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You haven't expressed interest in this idea"));
        if (interest.getStatus().isTerminal()) {
            throw new BadRequestException("This interest can no longer be withdrawn");
        }
        interest.setStatus(IdeaInterestStatus.WITHDRAWN);
        interest.setReviewedAt(Instant.now());
        ideaInterestRepository.saveAndFlush(interest);

        User applicantUser = userRepository.findById(userId).orElse(null);
        String applicantName = applicantUser != null ? applicantUser.getName() : "Someone";
        notificationService.notify(idea.getCreatorId(), NotificationType.idea_interest,
                "Interest withdrawn", applicantName + " withdrew their interest in \"" + idea.getTitle() + "\"",
                ideaId, userId);
    }

    @Transactional
    public IdeaInterestDto shortlistInterest(String creatorId, String interestId) {
        return transitionInterestStatus(creatorId, interestId, IdeaInterestStatus.SHORTLISTED,
                "Shortlisted for your idea", "shortlisted your interest in \"%s\"");
    }

    @Transactional
    public IdeaInterestDto rejectInterest(String creatorId, String interestId) {
        return transitionInterestStatus(creatorId, interestId, IdeaInterestStatus.REJECTED,
                "Update on your interest", "wasn't able to move forward with your interest in \"%s\"");
    }

    private IdeaInterestDto transitionInterestStatus(String creatorId, String interestId, IdeaInterestStatus newStatus,
                                                       String notificationTitle, String messageTemplate) {
        IdeaInterest interest = getInterestOrThrow(interestId);
        Idea idea = getEntityOrThrow(interest.getIdeaId());
        requireCreator(creatorId, idea);

        if (interest.getStatus().isTerminal()) {
            throw new BadRequestException("This interest has already been decided");
        }

        interest.setStatus(newStatus);
        interest.setReviewedAt(Instant.now());
        interest = ideaInterestRepository.saveAndFlush(interest);

        User creator = userRepository.findById(creatorId).orElse(null);
        String creatorName = creator != null ? creator.getName() : "The idea's creator";
        notificationService.notify(interest.getUserId(), NotificationType.idea_interest, notificationTitle,
                creatorName + " " + String.format(messageTemplate, idea.getTitle()), idea.getId(), creatorId);

        return toInterestDto(interest, idea, creatorId);
    }

    private IdeaInterest getInterestOrThrow(String interestId) {
        return ideaInterestRepository.findById(interestId)
                .orElseThrow(() -> new ResourceNotFoundException("Interest not found: " + interestId));
    }

    private IdeaInterestDto toInterestDto(IdeaInterest interest, Idea idea, String viewerId) {
        UserDto applicantDto = userService.getUser(interest.getUserId(), viewerId);

        List<ExperienceDto> experiences = interest.getExperienceIds().isEmpty() ? List.of()
                : userExperienceRepository.findByUser_IdOrderBySortOrderAsc(interest.getUserId()).stream()
                        .filter(e -> interest.getExperienceIds().contains(e.getId()))
                        .map(userMapper::toDto)
                        .toList();

        List<ProjectDto> projects = interest.getProjectIds().isEmpty() ? List.of()
                : userProjectRepository.findByUser_IdOrderBySortOrderAsc(interest.getUserId()).stream()
                        .filter(p -> interest.getProjectIds().contains(p.getId()))
                        .map(userMapper::toDto)
                        .toList();

        return new IdeaInterestDto(
                interest.getId(),
                idea.getId(),
                idea.getTitle(),
                applicantDto,
                interest.getStatus().getLabel(),
                interest.getContributionAreas().stream().map(ContributionArea::getLabel).collect(Collectors.toSet()),
                interest.getMessage(),
                new ArrayList<>(interest.getRelevantSkills()),
                experiences,
                projects,
                interest.getCreatedAt(),
                interest.getReviewedAt()
        );
    }

    public record IdeaMembers(List<UserDto> team, List<IdeaInterestDto> interests) {}

    @Transactional(readOnly = true)
    public IdeaMembers getMembers(String viewerId, String ideaId) {
        Idea idea = getEntityOrThrow(ideaId);
        List<UserDto> team = idea.getTeamMemberIds().stream()
                .map(id -> userRepository.findById(id).map(userMapper::toDto).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        // Only the idea's creator can see everyone's interest details; anyone else only ever
        // sees their own — an application's message/skills/experience are not public data.
        List<IdeaInterestDto> interests;
        if (idea.getCreatorId().equals(viewerId)) {
            interests = ideaInterestRepository.findByIdeaId(ideaId).stream()
                    .map(i -> toInterestDto(i, idea, viewerId))
                    .toList();
        } else {
            interests = ideaInterestRepository.findByIdeaIdAndUserId(ideaId, viewerId)
                    .map(i -> toInterestDto(i, idea, viewerId))
                    .map(List::of)
                    .orElse(List.of());
        }
        return new IdeaMembers(team, interests);
    }

    @Transactional
    public IdeaDto addToTeam(String callerId, String ideaId, String userId) {
        Idea idea = getEntityOrThrow(ideaId);
        requireCreator(callerId, idea);
        if (idea.getStartupId() != null) {
            throw new BadRequestException("This idea has already become a startup — manage its team from the startup page instead");
        }
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Set<String> team = new HashSet<>(idea.getTeamMemberIds());
        team.add(userId);
        idea.setTeamMemberIds(team);
        idea = ideaRepository.saveAndFlush(idea);

        ideaInterestRepository.findByIdeaIdAndUserId(ideaId, userId).ifPresent(interest -> {
            interest.setStatus(IdeaInterestStatus.ACCEPTED);
            interest.setReviewedAt(Instant.now());
            ideaInterestRepository.saveAndFlush(interest);
        });

        notificationService.notify(userId, NotificationType.idea_interest,
                "You joined a team", "You were added to the team for \"" + idea.getTitle() + "\"", ideaId, callerId);

        return toIdeaDto(idea);
    }

    @Transactional
    public IdeaDto removeFromTeam(String callerId, String ideaId, String userId) {
        Idea idea = getEntityOrThrow(ideaId);
        boolean isCreator = idea.getCreatorId().equals(callerId);
        boolean isSelf = callerId.equals(userId);
        if (!isCreator && !isSelf) {
            throw new ForbiddenException("Only the idea creator or the member themselves can remove a team member");
        }
        if (userId.equals(idea.getCreatorId())) {
            throw new BadRequestException("The idea creator cannot be removed from the team");
        }

        Set<String> team = new HashSet<>(idea.getTeamMemberIds());
        team.remove(userId);
        idea.setTeamMemberIds(team);
        return toIdeaDto(ideaRepository.saveAndFlush(idea));
    }

    @Transactional
    public StartupDto convertToStartup(String callerId, String ideaId, ConvertToStartupRequest request) {
        Idea idea = getEntityOrThrow(ideaId);
        if (!idea.getCreatorId().equals(callerId)) {
            throw new ForbiddenException("Only the idea's creator can convert it into a startup");
        }
        if (idea.getStartupId() != null) {
            throw new ConflictException("This idea has already been converted into a startup");
        }

        Startup startup = Startup.builder()
                .name(request.name() != null && !request.name().isBlank() ? request.name().trim() : idea.getTitle())
                .tagline(request.tagline())
                .sector(request.sector())
                .problem(idea.getProblem())
                .solution(idea.getSolution())
                .stage(StartupStage.IDEA)
                .ideaId(idea.getId())
                .chapterId(idea.getChapterId())
                .build();
        startup = startupRepository.saveAndFlush(startup);

        for (String memberId : idea.getTeamMemberIds()) {
            startupTeamMemberRepository.save(StartupTeamMember.builder()
                    .startupId(startup.getId())
                    .userId(memberId)
                    .teamRole(memberId.equals(idea.getCreatorId())
                            ? StartupTeamMember.TeamRole.FOUNDER : StartupTeamMember.TeamRole.MEMBER)
                    .status(StartupTeamMember.Status.ACTIVE)
                    .build());
        }

        idea.setStartupId(startup.getId());
        ideaRepository.save(idea);

        String startupId = startup.getId();
        String startupName = startup.getName();
        idea.getTeamMemberIds().forEach(memberId ->
                notificationService.notify(memberId, NotificationType.startup,
                        "Idea became a startup", "\"" + idea.getTitle() + "\" is now the startup " + startupName,
                        startupId, callerId));

        auditService.log(callerId, AuditAction.CREATE_STARTUP, "Startup", startup.getId(), null);

        return startupMapper.toDto(startup);
    }

    private void requireCreator(String userId, Idea idea) {
        if (!idea.getCreatorId().equals(userId)) {
            throw new ForbiddenException("Only the idea's creator can perform this action");
        }
    }

    private IdeaStage resolveStage(String label) {
        if (label == null || label.isBlank()) return IdeaStage.CONCEPT;
        return IdeaStage.fromLabel(label);
    }

    private Set<ContributionArea> resolveHelpNeeded(Set<String> labels) {
        if (labels == null) return new HashSet<>();
        return labels.stream().map(ContributionArea::fromLabel).collect(Collectors.toSet());
    }
}
