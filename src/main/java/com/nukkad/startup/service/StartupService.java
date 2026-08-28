package com.nukkad.startup.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.startup.dto.CreateStartupRequest;
import com.nukkad.startup.dto.CreateStartupRoleRequest;
import com.nukkad.startup.dto.StartupDto;
import com.nukkad.startup.dto.StartupJoinRequestDto;
import com.nukkad.startup.dto.StartupRoleDto;
import com.nukkad.startup.dto.StartupTeamMemberDto;
import com.nukkad.startup.dto.StartupUpdateDto;
import com.nukkad.startup.dto.UpdateStartupRequest;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupFollow;
import com.nukkad.startup.entity.StartupRole;
import com.nukkad.startup.entity.StartupRoleType;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.entity.StartupUpdate;
import com.nukkad.startup.mapper.StartupMapper;
import com.nukkad.startup.repository.StartupFollowRepository;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupRoleRepository;
import com.nukkad.startup.repository.StartupSpecifications;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import com.nukkad.startup.repository.StartupUpdateRepository;
import com.nukkad.user.dto.UserDto;
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

import java.time.Instant;
import java.util.List;

@Service
public class StartupService {

    private final StartupRepository startupRepository;
    private final StartupTeamMemberRepository teamMemberRepository;
    private final StartupUpdateRepository updateRepository;
    private final StartupRoleRepository roleRepository;
    private final StartupFollowRepository followRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final StartupMapper startupMapper;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;

    public StartupService(StartupRepository startupRepository,
                           StartupTeamMemberRepository teamMemberRepository,
                           StartupUpdateRepository updateRepository,
                           StartupRoleRepository roleRepository,
                           StartupFollowRepository followRepository,
                           UserRepository userRepository,
                           UserService userService,
                           StartupMapper startupMapper,
                           NotificationService notificationService,
                           FileStorageService fileStorageService) {
        this.startupRepository = startupRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.updateRepository = updateRepository;
        this.roleRepository = roleRepository;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.startupMapper = startupMapper;
        this.notificationService = notificationService;
        this.fileStorageService = fileStorageService;
    }

    public Startup getEntityOrThrow(String id) {
        return startupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Startup not found: " + id));
    }

    @Transactional(readOnly = true)
    public StartupDto getStartup(String id, String viewerId) {
        Startup startup = getEntityOrThrow(id);
        return startupMapper.toDto(startup, followRepository.existsByUserIdAndStartupId(viewerId, id));
    }

    @Transactional(readOnly = true)
    public Page<StartupDto> listStartups(String q, String sector, String stage, Boolean isRaising,
                                          String chapterId, String memberId, String viewerId, int page, int size) {
        Specification<Startup> spec = StartupSpecifications.combine(
                StartupSpecifications.search(q),
                StartupSpecifications.sector(sector),
                StartupSpecifications.stage(stage),
                StartupSpecifications.isRaising(isRaising),
                StartupSpecifications.chapterId(chapterId),
                StartupSpecifications.memberId(memberId)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return startupRepository.findAll(spec, pageable)
                .map(s -> startupMapper.toDto(s, followRepository.existsByUserIdAndStartupId(viewerId, s.getId())));
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
                .build();
        startup = startupRepository.saveAndFlush(startup);

        teamMemberRepository.save(StartupTeamMember.builder()
                .startupId(startup.getId())
                .userId(creatorId)
                .isFounder(true)
                .status(StartupTeamMember.Status.ACTIVE)
                .build());

        return startupMapper.toDto(startup);
    }

    @Transactional
    public StartupDto updateStartup(String userId, String startupId, UpdateStartupRequest request) {
        Startup startup = getEntityOrThrow(startupId);
        requireFounder(userId, startupId);

        if (request.name() != null) startup.setName(request.name());
        if (request.logoUrl() != null) startup.setLogoUrl(request.logoUrl());
        if (request.tagline() != null) startup.setTagline(request.tagline());
        if (request.sector() != null) startup.setSector(request.sector());
        if (request.problem() != null) startup.setProblem(request.problem());
        if (request.solution() != null) startup.setSolution(request.solution());
        if (request.traction() != null) startup.setTraction(request.traction());
        if (request.isRaising() != null) startup.setRaising(request.isRaising());
        if (request.stage() != null) startup.setStage(StartupStage.fromLabel(request.stage()));
        if (request.needs() != null) startup.setNeeds(new java.util.HashSet<>(request.needs()));

        return startupMapper.toDto(startupRepository.saveAndFlush(startup));
    }

    @Transactional
    public void deleteStartup(String userId, String startupId) {
        getEntityOrThrow(startupId);
        requireFounder(userId, startupId);
        startupRepository.deleteById(startupId);
    }

    @Transactional
    public StartupDto updateLogo(String founderId, String startupId, MultipartFile file, String publicBaseUrl) {
        Startup startup = getEntityOrThrow(startupId);
        requireFounder(founderId, startupId);
        String relativePath = fileStorageService.storeImage(file, "startup-logos");
        startup.setLogoUrl(publicBaseUrl + "/uploads/" + relativePath);
        return startupMapper.toDto(startupRepository.save(startup));
    }

    @Transactional
    public StartupDto removeLogo(String founderId, String startupId) {
        Startup startup = getEntityOrThrow(startupId);
        requireFounder(founderId, startupId);
        startup.setLogoUrl(null);
        return startupMapper.toDto(startupRepository.save(startup));
    }

    @Transactional(readOnly = true)
    public List<StartupTeamMemberDto> getMembers(String startupId) {
        getEntityOrThrow(startupId);
        return teamMemberRepository.findByStartupIdAndStatus(startupId, StartupTeamMember.Status.ACTIVE).stream()
                .map(startupMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public StartupTeamMemberDto getMyMembership(String userId, String startupId) {
        getEntityOrThrow(startupId);
        return teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .map(startupMapper::toDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<StartupJoinRequestDto> getJoinRequests(String founderId, String startupId) {
        Startup startup = getEntityOrThrow(startupId);
        requireFounder(founderId, startupId);
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
        Startup startup = getEntityOrThrow(startupId);
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

        teamMemberRepository.findByStartupIdAndIsFounderTrue(startupId).forEach(founder ->
                notificationService.notify(founder.getUserId(), NotificationType.startup,
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
        requireFounder(founderId, startup.getId());

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
    public StartupTeamMemberDto addMember(String founderId, String startupId, String userId, String roleId) {
        Startup startup = getEntityOrThrow(startupId);
        requireFounder(founderId, startupId);
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (roleId != null && !roleRepository.findById(roleId).map(r -> r.getStartupId().equals(startupId)).orElse(false)) {
            throw new BadRequestException("Invalid role for this startup");
        }

        StartupTeamMember member = teamMemberRepository.findByStartupIdAndUserId(startupId, userId).orElse(null);
        if (member != null && member.getStatus() == StartupTeamMember.Status.ACTIVE) {
            throw new ConflictException("Already a member of this startup");
        }
        if (member != null) {
            member.setStatus(StartupTeamMember.Status.ACTIVE);
            member.setRoleId(roleId);
            member.setReviewedAt(Instant.now());
        } else {
            member = StartupTeamMember.builder()
                    .startupId(startupId)
                    .userId(userId)
                    .status(StartupTeamMember.Status.ACTIVE)
                    .roleId(roleId)
                    .reviewedAt(Instant.now())
                    .build();
        }
        member = teamMemberRepository.saveAndFlush(member);

        notificationService.notify(userId, NotificationType.startup,
                "You're on the team", "You were added to the team for " + startup.getName(), startupId, founderId);

        return startupMapper.toDto(member);
    }

    @Transactional
    public void removeMember(String founderId, String startupId, String userId) {
        Startup startup = getEntityOrThrow(startupId);
        requireFounder(founderId, startupId);
        if (userId.equals(founderId)) {
            throw new BadRequestException("Use leave team to remove yourself");
        }
        StartupTeamMember member = teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .filter(m -> m.getStatus() == StartupTeamMember.Status.ACTIVE)
                .orElseThrow(() -> new BadRequestException("This user is not a member of this startup"));
        if (member.isFounder()) {
            throw new BadRequestException("Founders can't be removed");
        }
        teamMemberRepository.delete(member);

        notificationService.notify(userId, NotificationType.startup,
                "Removed from the team", "You were removed from the team for " + startup.getName(), startupId, founderId);
    }

    public record FollowResult(boolean following) {}

    @Transactional
    public FollowResult toggleFollow(String userId, String startupId) {
        getEntityOrThrow(startupId);
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
    public List<StartupUpdateDto> getUpdates(String startupId) {
        getEntityOrThrow(startupId);
        return updateRepository.findByStartupIdOrderByCreatedAtDesc(startupId, PageRequest.of(0, 100))
                .map(startupMapper::toDto)
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<StartupRoleDto> getRoles(String startupId) {
        getEntityOrThrow(startupId);
        return roleRepository.findByStartupId(startupId).stream().map(startupMapper::toDto).toList();
    }

    @Transactional
    public StartupRoleDto createRole(String userId, String startupId, CreateStartupRoleRequest request) {
        getEntityOrThrow(startupId);
        requireFounder(userId, startupId);
        StartupRole role = roleRepository.saveAndFlush(StartupRole.builder()
                .startupId(startupId)
                .title(request.title())
                .type(StartupRoleType.fromLabel(request.type()))
                .location(request.location())
                .remote(request.remote())
                .build());
        return startupMapper.toDto(role);
    }

    private void requireFounder(String userId, String startupId) {
        boolean isFounder = teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .map(StartupTeamMember::isFounder)
                .orElse(false);
        if (!isFounder) throw new ForbiddenException("Only a founder of this startup can perform this action");
    }

    private void requireTeamMember(String userId, String startupId) {
        boolean isMember = teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .map(m -> m.getStatus() == StartupTeamMember.Status.ACTIVE)
                .orElse(false);
        if (!isMember) throw new ForbiddenException("Only a team member of this startup can perform this action");
    }
}
