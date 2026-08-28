package com.nukkad.opportunity.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.opportunity.dto.ApplicationDto;
import com.nukkad.opportunity.dto.ApplyToOpportunityRequest;
import com.nukkad.opportunity.dto.OpportunityDto;
import com.nukkad.opportunity.dto.PostOpportunityRequest;
import com.nukkad.opportunity.dto.UpdateOpportunityRequest;
import com.nukkad.opportunity.entity.ApplicationStatus;
import com.nukkad.opportunity.entity.Opportunity;
import com.nukkad.opportunity.entity.OpportunityApplicant;
import com.nukkad.opportunity.entity.OpportunityInterest;
import com.nukkad.opportunity.entity.OpportunityType;
import com.nukkad.opportunity.mapper.OpportunityMapper;
import com.nukkad.opportunity.repository.OpportunityApplicantRepository;
import com.nukkad.opportunity.repository.OpportunityInterestRepository;
import com.nukkad.opportunity.repository.OpportunityRepository;
import com.nukkad.opportunity.repository.OpportunitySpecifications;
import com.nukkad.user.dto.ExperienceDto;
import com.nukkad.user.dto.ProjectDto;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.Availability;
import com.nukkad.user.entity.User;
import com.nukkad.user.entity.UserExperience;
import com.nukkad.user.entity.UserProject;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.UserExperienceRepository;
import com.nukkad.user.repository.UserProjectRepository;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OpportunityService {

    private final OpportunityRepository opportunityRepository;
    private final OpportunityApplicantRepository applicantRepository;
    private final OpportunityInterestRepository interestRepository;
    private final UserRepository userRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserProjectRepository userProjectRepository;
    private final UserService userService;
    private final UserMapper userMapper;
    private final OpportunityMapper opportunityMapper;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public OpportunityService(OpportunityRepository opportunityRepository,
                               OpportunityApplicantRepository applicantRepository,
                               OpportunityInterestRepository interestRepository,
                               UserRepository userRepository,
                               UserExperienceRepository userExperienceRepository,
                               UserProjectRepository userProjectRepository,
                               UserService userService,
                               UserMapper userMapper,
                               OpportunityMapper opportunityMapper,
                               NotificationService notificationService,
                               AuditService auditService) {
        this.opportunityRepository = opportunityRepository;
        this.applicantRepository = applicantRepository;
        this.interestRepository = interestRepository;
        this.userRepository = userRepository;
        this.userExperienceRepository = userExperienceRepository;
        this.userProjectRepository = userProjectRepository;
        this.userService = userService;
        this.userMapper = userMapper;
        this.opportunityMapper = opportunityMapper;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    public Opportunity getEntityOrThrow(String id) {
        return opportunityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Opportunity not found: " + id));
    }

    private OpportunityDto toOpportunityDto(Opportunity opportunity, String viewerId) {
        var existingApplication = applicantRepository.findByOpportunityIdAndUserId(opportunity.getId(), viewerId);
        boolean hasApplied = existingApplication.isPresent();
        String applicationStatus = existingApplication.map(a -> a.getStatus().getLabel()).orElse(null);
        boolean hasExpressedInterest = interestRepository.existsByOpportunityIdAndUserId(opportunity.getId(), viewerId);
        int applicantCount = (int) applicantRepository.countByOpportunityId(opportunity.getId());
        int interestCount = (int) interestRepository.countByOpportunityId(opportunity.getId());
        return opportunityMapper.toDto(opportunity, hasApplied, hasExpressedInterest, applicationStatus, applicantCount, interestCount);
    }

    @Transactional(readOnly = true)
    public OpportunityDto getOpportunity(String id, String viewerId) {
        return toOpportunityDto(getEntityOrThrow(id), viewerId);
    }

    @Transactional(readOnly = true)
    public Page<OpportunityDto> listOpportunities(String q, String type, Boolean remote, String chapterId,
                                                   String viewerId, int page, int size) {
        Specification<Opportunity> spec = OpportunitySpecifications.combine(
                OpportunitySpecifications.search(q),
                OpportunitySpecifications.type(type),
                OpportunitySpecifications.remote(remote),
                OpportunitySpecifications.chapterId(chapterId)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return opportunityRepository.findAll(spec, pageable).map(o -> toOpportunityDto(o, viewerId));
    }

    @Transactional
    public OpportunityDto postOpportunity(String userId, PostOpportunityRequest request) {
        User poster = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Opportunity opportunity = Opportunity.builder()
                .title(request.title().trim())
                .type(OpportunityType.fromLabel(request.type()))
                .startupId(request.startupId())
                .organizationName(request.organizationName().trim())
                .location(request.location())
                .remote(request.remote())
                .description(request.description())
                .compensation(request.compensation())
                .postedByUserId(userId)
                .chapterId(poster.getChapterId())
                .requirements(request.requirements() == null ? new ArrayList<>() : new ArrayList<>(request.requirements()))
                .build();

        opportunity = opportunityRepository.saveAndFlush(opportunity);
        auditService.log(userId, AuditAction.CREATE_OPPORTUNITY, "Opportunity", opportunity.getId(), null);
        return opportunityMapper.toDto(opportunity);
    }

    @Transactional
    public OpportunityDto updateOpportunity(String userId, String id, UpdateOpportunityRequest request) {
        Opportunity opportunity = getEntityOrThrow(id);
        requirePoster(userId, opportunity);

        if (request.title() != null) opportunity.setTitle(request.title());
        if (request.type() != null) opportunity.setType(OpportunityType.fromLabel(request.type()));
        if (request.startupId() != null) opportunity.setStartupId(request.startupId());
        if (request.organizationName() != null) opportunity.setOrganizationName(request.organizationName());
        if (request.location() != null) opportunity.setLocation(request.location());
        if (request.remote() != null) opportunity.setRemote(request.remote());
        if (request.description() != null) opportunity.setDescription(request.description());
        if (request.compensation() != null) opportunity.setCompensation(request.compensation());
        if (request.requirements() != null) opportunity.setRequirements(new ArrayList<>(request.requirements()));

        return opportunityMapper.toDto(opportunityRepository.saveAndFlush(opportunity));
    }

    @Transactional
    public void deleteOpportunity(String userId, String id) {
        Opportunity opportunity = getEntityOrThrow(id);
        requirePoster(userId, opportunity);
        opportunityRepository.delete(opportunity);
    }

    @Transactional
    public void expressInterest(String userId, String id) {
        Opportunity opportunity = getEntityOrThrow(id);
        if (!interestRepository.existsByOpportunityIdAndUserId(id, userId)) {
            interestRepository.save(OpportunityInterest.builder().opportunityId(id).userId(userId).build());
            notificationService.notify(opportunity.getPostedByUserId(), NotificationType.opportunity,
                    "New interest in your opportunity", "Someone is interested in \"" + opportunity.getTitle() + "\"", id, userId);
        }
    }

    // ---- Applications ----

    @Transactional
    public ApplicationDto apply(String userId, String id, ApplyToOpportunityRequest request) {
        Opportunity opportunity = getEntityOrThrow(id);
        User applicantUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        var existing = applicantRepository.findByOpportunityIdAndUserId(id, userId);
        if (existing.isPresent() && existing.get().getStatus() != ApplicationStatus.WITHDRAWN) {
            throw new BadRequestException("You've already applied to this opportunity");
        }

        Set<String> skills = (request.relevantSkills() == null || request.relevantSkills().isEmpty())
                ? new LinkedHashSet<>(applicantUser.getSkills())
                : new LinkedHashSet<>(request.relevantSkills());

        List<String> experienceIds = validateOwnedExperienceIds(userId, request.experienceIds());
        List<String> projectIds = validateOwnedProjectIds(userId, request.projectIds());

        Availability availability = request.availability() != null && !request.availability().isBlank()
                ? Availability.fromLabel(request.availability())
                : applicantUser.getAvailability();

        OpportunityApplicant applicant = existing.orElseGet(() ->
                OpportunityApplicant.builder().opportunityId(id).userId(userId).build());
        applicant.setStatus(ApplicationStatus.PENDING);
        applicant.setWhyInterested(request.whyInterested().trim());
        applicant.setWhyGoodFit(request.whyGoodFit().trim());
        applicant.setRelevantSkills(new ArrayList<>(skills));
        applicant.setExperienceIds(experienceIds);
        applicant.setProjectIds(projectIds);
        applicant.setAvailability(availability);
        applicant.setExpectedCommitment(request.expectedCommitment());
        applicant.setAdditionalMessage(request.additionalMessage());
        applicant.setReviewedAt(null);
        applicant = applicantRepository.saveAndFlush(applicant);

        auditService.log(userId, AuditAction.APPLY_OPPORTUNITY, "Opportunity", id, null);
        notificationService.notify(opportunity.getPostedByUserId(), NotificationType.opportunity,
                "New application", applicantUser.getName() + " applied to \"" + opportunity.getTitle() + "\"", id, userId);

        return toApplicationDto(applicant, opportunity, userId);
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
    public void withdrawApplication(String userId, String opportunityId) {
        Opportunity opportunity = getEntityOrThrow(opportunityId);
        OpportunityApplicant applicant = applicantRepository.findByOpportunityIdAndUserId(opportunityId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You haven't applied to this opportunity"));
        if (applicant.getStatus().isTerminal()) {
            throw new BadRequestException("This application can no longer be withdrawn");
        }
        applicant.setStatus(ApplicationStatus.WITHDRAWN);
        applicant.setReviewedAt(Instant.now());
        applicantRepository.saveAndFlush(applicant);

        User applicantUser = userRepository.findById(userId).orElse(null);
        String applicantName = applicantUser != null ? applicantUser.getName() : "An applicant";
        notificationService.notify(opportunity.getPostedByUserId(), NotificationType.opportunity,
                "Application withdrawn", applicantName + " withdrew their application for \"" + opportunity.getTitle() + "\"",
                opportunityId, userId);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationDto> listApplications(String ownerUserId, String opportunityId, String statusFilter, int page, int size) {
        Opportunity opportunity = getEntityOrThrow(opportunityId);
        requirePoster(ownerUserId, opportunity);

        Pageable pageable = PageRequest.of(page, size);
        Page<OpportunityApplicant> applicants = (statusFilter == null || statusFilter.isBlank())
                ? applicantRepository.findByOpportunityIdOrderByCreatedAtDesc(opportunityId, pageable)
                : applicantRepository.findByOpportunityIdAndStatusOrderByCreatedAtDesc(
                        opportunityId, ApplicationStatus.fromLabel(statusFilter), pageable);

        return applicants.map(a -> toApplicationDto(a, opportunity, ownerUserId));
    }

    @Transactional(readOnly = true)
    public ApplicationDto getApplication(String viewerId, String applicationId) {
        OpportunityApplicant applicant = getApplicantOrThrow(applicationId);
        Opportunity opportunity = getEntityOrThrow(applicant.getOpportunityId());
        requireApplicantOrPoster(viewerId, applicant, opportunity);
        return toApplicationDto(applicant, opportunity, viewerId);
    }

    @Transactional
    public ApplicationDto shortlistApplication(String ownerUserId, String applicationId) {
        return transitionStatus(ownerUserId, applicationId, ApplicationStatus.SHORTLISTED,
                "Application shortlisted", "shortlisted your application for \"%s\"");
    }

    @Transactional
    public ApplicationDto acceptApplication(String ownerUserId, String applicationId) {
        return transitionStatus(ownerUserId, applicationId, ApplicationStatus.ACCEPTED,
                "Application accepted!", "accepted your application for \"%s\" — you can now message them");
    }

    @Transactional
    public ApplicationDto rejectApplication(String ownerUserId, String applicationId) {
        return transitionStatus(ownerUserId, applicationId, ApplicationStatus.REJECTED,
                "Application update", "wasn't able to move forward with your application for \"%s\"");
    }

    private ApplicationDto transitionStatus(String ownerUserId, String applicationId, ApplicationStatus newStatus,
                                             String notificationTitle, String messageTemplate) {
        OpportunityApplicant applicant = getApplicantOrThrow(applicationId);
        Opportunity opportunity = getEntityOrThrow(applicant.getOpportunityId());
        requirePoster(ownerUserId, opportunity);

        if (applicant.getStatus().isTerminal()) {
            throw new BadRequestException("This application has already been decided");
        }

        applicant.setStatus(newStatus);
        applicant.setReviewedAt(Instant.now());
        applicant = applicantRepository.saveAndFlush(applicant);

        User owner = userRepository.findById(ownerUserId).orElse(null);
        String ownerName = owner != null ? owner.getName() : "The opportunity owner";
        notificationService.notify(applicant.getUserId(), NotificationType.opportunity, notificationTitle,
                ownerName + " " + String.format(messageTemplate, opportunity.getTitle()), opportunity.getId(), ownerUserId);

        return toApplicationDto(applicant, opportunity, ownerUserId);
    }

    private OpportunityApplicant getApplicantOrThrow(String applicationId) {
        return applicantRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    private void requireApplicantOrPoster(String viewerId, OpportunityApplicant applicant, Opportunity opportunity) {
        if (!applicant.getUserId().equals(viewerId) && !opportunity.getPostedByUserId().equals(viewerId)) {
            throw new ForbiddenException("You don't have access to this application");
        }
    }

    private ApplicationDto toApplicationDto(OpportunityApplicant applicant, Opportunity opportunity, String viewerId) {
        UserDto applicantDto = userService.getUser(applicant.getUserId(), viewerId);

        List<ExperienceDto> experiences = applicant.getExperienceIds().isEmpty() ? List.of()
                : userExperienceRepository.findByUser_IdOrderBySortOrderAsc(applicant.getUserId()).stream()
                        .filter(e -> applicant.getExperienceIds().contains(e.getId()))
                        .map(userMapper::toDto)
                        .toList();

        List<ProjectDto> projects = applicant.getProjectIds().isEmpty() ? List.of()
                : userProjectRepository.findByUser_IdOrderBySortOrderAsc(applicant.getUserId()).stream()
                        .filter(p -> applicant.getProjectIds().contains(p.getId()))
                        .map(userMapper::toDto)
                        .toList();

        return new ApplicationDto(
                applicant.getId(),
                opportunity.getId(),
                opportunity.getTitle(),
                applicantDto,
                applicant.getStatus().getLabel(),
                applicant.getWhyInterested(),
                applicant.getWhyGoodFit(),
                new ArrayList<>(applicant.getRelevantSkills()),
                experiences,
                projects,
                applicant.getAvailability() == null ? null : applicant.getAvailability().getLabel(),
                applicant.getExpectedCommitment(),
                applicant.getAdditionalMessage(),
                applicant.getCreatedAt(),
                applicant.getReviewedAt()
        );
    }

    @Transactional(readOnly = true)
    public Page<OpportunityDto> listMyPosted(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return opportunityRepository.findByPostedByUserId(userId, pageable).map(o -> toOpportunityDto(o, userId));
    }

    @Transactional(readOnly = true)
    public Page<OpportunityDto> listMyApplications(String userId, int page, int size) {
        LinkedHashSet<String> opportunityIds = new LinkedHashSet<>();
        applicantRepository.findByUserId(userId).forEach(a -> opportunityIds.add(a.getOpportunityId()));
        interestRepository.findByUserId(userId).forEach(i -> opportunityIds.add(i.getOpportunityId()));

        List<OpportunityDto> all = opportunityIds.stream()
                .map(opportunityRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(o -> toOpportunityDto(o, userId))
                .toList();

        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), all.size());
    }

    private void requirePoster(String userId, Opportunity opportunity) {
        if (!opportunity.getPostedByUserId().equals(userId)) {
            throw new ForbiddenException("Only the user who posted this opportunity can perform this action");
        }
    }
}
