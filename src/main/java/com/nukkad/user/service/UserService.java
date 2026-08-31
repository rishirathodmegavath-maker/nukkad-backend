package com.nukkad.user.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.dto.AchievementDto;
import com.nukkad.user.dto.CertificationDto;
import com.nukkad.user.dto.EducationDto;
import com.nukkad.user.dto.ExperienceDto;
import com.nukkad.user.dto.ProfileSections;
import com.nukkad.user.dto.ProjectDto;
import com.nukkad.user.dto.PublicationDto;
import com.nukkad.user.dto.RecommendationDto;
import com.nukkad.user.dto.UpdateUserRequest;
import com.nukkad.user.dto.UpsertAchievementRequest;
import com.nukkad.user.dto.UpsertCertificationRequest;
import com.nukkad.user.dto.UpsertEducationRequest;
import com.nukkad.user.dto.UpsertExperienceRequest;
import com.nukkad.user.dto.UpsertProjectRequest;
import com.nukkad.user.dto.UpsertPublicationRequest;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.Availability;
import com.nukkad.user.entity.Connection;
import com.nukkad.user.entity.LookingFor;
import com.nukkad.user.entity.OpenTo;
import com.nukkad.user.entity.ProfileSection;
import com.nukkad.user.entity.User;
import com.nukkad.user.entity.UserAchievement;
import com.nukkad.user.entity.MutedAccount;
import com.nukkad.user.entity.UserBlock;
import com.nukkad.user.entity.UserCertification;
import com.nukkad.user.entity.UserEducation;
import com.nukkad.user.entity.UserExperience;
import com.nukkad.user.entity.UserProject;
import com.nukkad.user.entity.UserPublication;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserAchievementRepository;
import com.nukkad.user.repository.MutedAccountRepository;
import com.nukkad.user.repository.UserBlockRepository;
import com.nukkad.user.repository.UserCertificationRepository;
import com.nukkad.user.repository.UserEducationRepository;
import com.nukkad.user.repository.UserExperienceRepository;
import com.nukkad.user.repository.UserFollowRepository;
import com.nukkad.user.repository.UserProjectRepository;
import com.nukkad.user.repository.UserPublicationRepository;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.repository.UserSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.nukkad.user.entity.UserFollow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ConnectionRepository connectionRepository;
    private final UserFollowRepository userFollowRepository;
    private final UserBlockRepository userBlockRepository;
    private final MutedAccountRepository mutedAccountRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserEducationRepository userEducationRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserProjectRepository userProjectRepository;
    private final UserCertificationRepository userCertificationRepository;
    private final UserPublicationRepository userPublicationRepository;
    private final UserMapper userMapper;
    private final ProfileCompletenessCalculator profileCompletenessCalculator;
    private final UserEndorsementService userEndorsementService;
    private final UserRecommendationService userRecommendationService;
    private final ProfilePrivacyService profilePrivacyService;
    private final UserPrivacySettingsService userPrivacySettingsService;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;

    public UserService(UserRepository userRepository,
                        ConnectionRepository connectionRepository,
                        UserFollowRepository userFollowRepository,
                        UserBlockRepository userBlockRepository,
                        MutedAccountRepository mutedAccountRepository,
                        UserExperienceRepository userExperienceRepository,
                        UserEducationRepository userEducationRepository,
                        UserAchievementRepository userAchievementRepository,
                        UserProjectRepository userProjectRepository,
                        UserCertificationRepository userCertificationRepository,
                        UserPublicationRepository userPublicationRepository,
                        UserMapper userMapper,
                        ProfileCompletenessCalculator profileCompletenessCalculator,
                        UserEndorsementService userEndorsementService,
                        UserRecommendationService userRecommendationService,
                        ProfilePrivacyService profilePrivacyService,
                        UserPrivacySettingsService userPrivacySettingsService,
                        NotificationService notificationService,
                        FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.connectionRepository = connectionRepository;
        this.userFollowRepository = userFollowRepository;
        this.userBlockRepository = userBlockRepository;
        this.mutedAccountRepository = mutedAccountRepository;
        this.userExperienceRepository = userExperienceRepository;
        this.userEducationRepository = userEducationRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.userProjectRepository = userProjectRepository;
        this.userCertificationRepository = userCertificationRepository;
        this.userPublicationRepository = userPublicationRepository;
        this.userMapper = userMapper;
        this.profileCompletenessCalculator = profileCompletenessCalculator;
        this.userEndorsementService = userEndorsementService;
        this.userRecommendationService = userRecommendationService;
        this.profilePrivacyService = profilePrivacyService;
        this.userPrivacySettingsService = userPrivacySettingsService;
        this.notificationService = notificationService;
        this.fileStorageService = fileStorageService;
    }

    public User getEntityOrThrow(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /**
     * Owner always sees everything (short-circuits every privacy check below, so the hot
     * /me path never pays the extra lookup cost). For any other viewer, each section is
     * individually gated by ProfilePrivacyService before it ever reaches the mapper —
     * UserMapper itself stays privacy-agnostic.
     */
    private UserDto toFullDto(User user, String connectionStatus, Boolean isFollowing, String viewerId) {
        boolean isSelf = viewerId != null && viewerId.equals(user.getId());

        List<UserExperience> experiences = isSelf || profilePrivacyService.isVisible(user.getId(), viewerId, ProfileSection.EXPERIENCE, connectionStatus)
                ? userExperienceRepository.findByUser_IdOrderBySortOrderAsc(user.getId()) : List.of();
        List<UserEducation> education = isSelf || profilePrivacyService.isVisible(user.getId(), viewerId, ProfileSection.EDUCATION, connectionStatus)
                ? userEducationRepository.findByUser_IdOrderBySortOrderAsc(user.getId()) : List.of();
        List<UserAchievement> achievements = isSelf || profilePrivacyService.isVisible(user.getId(), viewerId, ProfileSection.ACHIEVEMENTS, connectionStatus)
                ? userAchievementRepository.findByUser_IdOrderBySortOrderAsc(user.getId()) : List.of();
        List<UserProject> projects = isSelf || profilePrivacyService.isVisible(user.getId(), viewerId, ProfileSection.PROJECTS, connectionStatus)
                ? userProjectRepository.findByUser_IdOrderBySortOrderAsc(user.getId()) : List.of();
        List<UserCertification> certifications = isSelf || profilePrivacyService.isVisible(user.getId(), viewerId, ProfileSection.CERTIFICATIONS, connectionStatus)
                ? userCertificationRepository.findByUser_IdOrderBySortOrderAsc(user.getId()) : List.of();
        List<UserPublication> publications = isSelf || profilePrivacyService.isVisible(user.getId(), viewerId, ProfileSection.PUBLICATIONS, connectionStatus)
                ? userPublicationRepository.findByUser_IdOrderBySortOrderAsc(user.getId()) : List.of();

        ProfileSections sections = new ProfileSections(experiences, education, achievements, projects, certifications, publications);
        int completeness = profileCompletenessCalculator.compute(user, sections);
        var endorsementSummary = userEndorsementService.summary(user.getId(), viewerId);
        List<RecommendationDto> recommendations = isSelf || profilePrivacyService.isVisible(user.getId(), viewerId, ProfileSection.RECOMMENDATIONS, connectionStatus)
                ? userRecommendationService.listPublic(user.getId()) : List.of();
        boolean socialLinksVisible = isSelf || profilePrivacyService.isVisible(user.getId(), viewerId, ProfileSection.SOCIAL_LINKS, connectionStatus);

        return userMapper.toDto(user, connectionStatus, isFollowing, sections, completeness, endorsementSummary, recommendations, socialLinksVisible);
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String userId) {
        return toFullDto(getEntityOrThrow(userId), null, null, userId);
    }

    @Transactional
    public UserDto updateCurrentUser(String userId, UpdateUserRequest request) {
        User user = getEntityOrThrow(userId);

        if (request.name() != null) user.setName(request.name());
        if (request.avatarUrl() != null) user.setAvatarUrl(request.avatarUrl());
        if (request.headline() != null) user.setHeadline(request.headline());
        if (request.role() != null) user.setRole(request.role());
        if (request.collegeOrCompany() != null) user.setCollegeOrCompany(request.collegeOrCompany());
        if (request.location() != null) user.setLocation(request.location());
        if (request.experienceYears() != null) user.setExperienceYears(request.experienceYears());
        if (request.goals() != null) user.setGoals(request.goals());
        if (request.bio() != null) user.setBio(request.bio());
        if (request.chapterId() != null) user.setChapterId(request.chapterId().isBlank() ? null : request.chapterId());

        if (request.availability() != null) {
            try {
                user.setAvailability(request.availability().isBlank() ? null : Availability.fromLabel(request.availability()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid availability value: " + request.availability());
            }
        }

        if (request.skills() != null) {
            user.setSkills(request.skills().stream().map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet()));
        }

        if (request.lookingFor() != null) {
            Set<LookingFor> resolved = request.lookingFor().stream()
                    .map(this::resolveLookingFor)
                    .collect(Collectors.toSet());
            user.setLookingFor(resolved);
        }

        if (request.openTo() != null) {
            Set<OpenTo> resolved = request.openTo().stream()
                    .map(this::resolveOpenTo)
                    .collect(Collectors.toSet());
            user.setOpenTo(resolved);
        }

        if (request.socialLinks() != null) {
            java.util.Map<com.nukkad.user.entity.SocialPlatform, String> resolved = new java.util.HashMap<>();
            request.socialLinks().forEach((platform, url) -> {
                if (url == null || url.isBlank()) return;
                try {
                    resolved.put(UserMapper.parsePlatform(platform), url.trim());
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid social platform: " + platform);
                }
            });
            user.setSocialLinks(resolved);
        }

        return toFullDto(userRepository.save(user), null, null, userId);
    }

    @Transactional
    public UserDto updateAvatar(String userId, MultipartFile file) {
        User user = getEntityOrThrow(userId);
        user.setAvatarUrl(fileStorageService.storeImage(file, "avatars"));
        return toFullDto(userRepository.save(user), null, null, userId);
    }

    @Transactional
    public UserDto removeAvatar(String userId) {
        User user = getEntityOrThrow(userId);
        user.setAvatarUrl(null);
        return toFullDto(userRepository.save(user), null, null, userId);
    }

    @Transactional
    public UserDto updateCover(String userId, MultipartFile file) {
        User user = getEntityOrThrow(userId);
        user.setCoverUrl(fileStorageService.storeImage(file, "covers"));
        return toFullDto(userRepository.save(user), null, null, userId);
    }

    @Transactional
    public UserDto removeCover(String userId) {
        User user = getEntityOrThrow(userId);
        user.setCoverUrl(null);
        return toFullDto(userRepository.save(user), null, null, userId);
    }

    private LookingFor resolveLookingFor(String label) {
        return java.util.Arrays.stream(LookingFor.values())
                .filter(v -> v.getLabel().equalsIgnoreCase(label.trim()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Invalid lookingFor value: " + label));
    }

    private OpenTo resolveOpenTo(String label) {
        return java.util.Arrays.stream(OpenTo.values())
                .filter(v -> v.getLabel().equalsIgnoreCase(label.trim()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Invalid openTo value: " + label));
    }

    @Transactional(readOnly = true)
    public UserDto getUser(String id, String viewerId) {
        User user = getEntityOrThrow(id);
        String connectionStatus = null;
        Boolean isFollowing = null;
        if (viewerId != null && !viewerId.equals(id)) {
            String a = viewerId.compareTo(id) < 0 ? viewerId : id;
            String b = viewerId.compareTo(id) < 0 ? id : viewerId;
            connectionStatus = resolveConnectionStatus(connectionRepository.findByUserAIdAndUserBId(a, b), viewerId);
            isFollowing = userFollowRepository.existsByFollowerIdAndFolloweeId(viewerId, id);
            if (userPrivacySettingsService.isProfileRestricted(id, viewerId, "CONNECTED".equals(connectionStatus))) {
                return userMapper.toRestrictedDto(user, connectionStatus, isFollowing);
            }
        }
        return toFullDto(user, connectionStatus, isFollowing, viewerId);
    }

    /** NONE / PENDING_OUTGOING (viewer requested) / PENDING_INCOMING (viewer was requested) / CONNECTED. */
    private String resolveConnectionStatus(java.util.Optional<Connection> connection, String viewerId) {
        if (connection.isEmpty()) return "NONE";
        Connection c = connection.get();
        if (c.getStatus() == Connection.Status.ACCEPTED) return "CONNECTED";
        if (c.getStatus() == Connection.Status.PENDING) {
            return c.getRequestedBy().equals(viewerId) ? "PENDING_OUTGOING" : "PENDING_INCOMING";
        }
        return "NONE";
    }

    @Transactional(readOnly = true)
    public Page<UserDto> listUsers(String viewerId, String q, String skill, String collegeOrCompany,
                                    String location, String role, String lookingFor, Integer minExperience,
                                    String chapterId, int page, int size) {
        Specification<User> spec = UserSpecifications.combine(
                UserSpecifications.excludeId(viewerId),
                UserSpecifications.excludeIds(userBlockRepository.findBlockedEitherWayIds(viewerId)),
                UserSpecifications.search(q),
                UserSpecifications.hasSkill(skill),
                UserSpecifications.collegeOrCompany(collegeOrCompany),
                UserSpecifications.location(location),
                UserSpecifications.role(role),
                UserSpecifications.lookingFor(lookingFor),
                UserSpecifications.minExperience(minExperience),
                UserSpecifications.chapterId(chapterId)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> results = userRepository.findAll(spec, pageable);
        var statusMap = bulkResolveConnectionStatuses(viewerId, results.getContent());
        return results.map(u -> userMapper.toDto(u, statusMap.getOrDefault(u.getId(), "NONE"), null));
    }

    /** Batch connection-status lookup for list views (avoids N+1 queries). */
    private java.util.Map<String, String> bulkResolveConnectionStatuses(String viewerId, List<User> users) {
        if (viewerId == null) return java.util.Map.of();
        List<String> otherIds = users.stream().map(User::getId).filter(id -> !id.equals(viewerId)).toList();
        if (otherIds.isEmpty()) return java.util.Map.of();
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (Connection c : connectionRepository.findAllInvolvingViewer(viewerId, otherIds)) {
            String otherId = c.getUserAId().equals(viewerId) ? c.getUserBId() : c.getUserAId();
            result.put(otherId, resolveConnectionStatus(java.util.Optional.of(c), viewerId));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<UserDto> listSuggested(String viewerId, int limit) {
        User viewer = getEntityOrThrow(viewerId);

        var blockedIds = userBlockRepository.findBlockedEitherWayIds(viewerId);
        Specification<User> spec = UserSpecifications.combine(
                UserSpecifications.excludeId(viewerId),
                UserSpecifications.excludeIds(blockedIds),
                viewer.getChapterId() != null ? UserSpecifications.chapterId(viewer.getChapterId()) : null
        );
        Pageable pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 50), Sort.by(Sort.Direction.DESC, "connectionsCount"));
        List<User> candidates = new java.util.ArrayList<>(userRepository.findAll(spec, pageable).getContent());

        if (candidates.size() < limit && viewer.getChapterId() != null) {
            Specification<User> fallback = UserSpecifications.combine(UserSpecifications.excludeId(viewerId), UserSpecifications.excludeIds(blockedIds));
            List<User> more = userRepository.findAll(fallback, PageRequest.of(0, limit)).getContent();
            for (User u : more) {
                if (candidates.size() >= limit) break;
                if (candidates.stream().noneMatch(c -> c.getId().equals(u.getId()))) candidates.add(u);
            }
        }

        var statusMap = bulkResolveConnectionStatuses(viewerId, candidates);
        return candidates.stream().map(u -> userMapper.toDto(u, statusMap.getOrDefault(u.getId(), "NONE"), null)).toList();
    }

    public record ConnectResult(String status, int connectionsCount) {}

    @Transactional
    public ConnectResult toggleConnect(String viewerId, String targetId) {
        if (viewerId.equals(targetId)) throw new BadRequestException("Cannot connect to yourself");
        User viewer = getEntityOrThrow(viewerId);
        User target = getEntityOrThrow(targetId);

        String a = viewerId.compareTo(targetId) < 0 ? viewerId : targetId;
        String b = viewerId.compareTo(targetId) < 0 ? targetId : viewerId;

        var existing = connectionRepository.findByUserAIdAndUserBId(a, b);

        if (existing.isEmpty()) {
            if (userBlockRepository.existsBetween(viewerId, targetId)) {
                throw new ForbiddenException("You can't connect with this user");
            }
            boolean hasMutualConnection = getMutualConnections(viewerId, targetId, 1).totalCount() > 0;
            if (!userPrivacySettingsService.canConnect(targetId, hasMutualConnection)) {
                throw new ForbiddenException("This user isn't accepting connection requests right now");
            }
            // No relationship yet: send a request, pending until the target accepts.
            Connection connection = Connection.builder()
                    .userAId(a)
                    .userBId(b)
                    .requestedBy(viewerId)
                    .status(Connection.Status.PENDING)
                    .build();
            connectionRepository.save(connection);
            notificationService.notify(targetId, NotificationType.connection,
                    "New connection request", viewer.getName() + " wants to connect with you", viewerId, viewerId);
            return new ConnectResult("PENDING_OUTGOING", viewer.getConnectionsCount());
        }

        Connection connection = existing.get();

        if (connection.getStatus() == Connection.Status.PENDING && connection.getRequestedBy().equals(viewerId)) {
            // Viewer is cancelling their own outgoing request.
            connectionRepository.delete(connection);
            return new ConnectResult("NONE", viewer.getConnectionsCount());
        }

        if (connection.getStatus() == Connection.Status.PENDING) {
            // Viewer is the target of a pending request: accept it.
            connection.setStatus(Connection.Status.ACCEPTED);
            connectionRepository.save(connection);
            viewer.setConnectionsCount(viewer.getConnectionsCount() + 1);
            target.setConnectionsCount(target.getConnectionsCount() + 1);
            userRepository.save(viewer);
            userRepository.save(target);
            notificationService.notify(targetId, NotificationType.connection,
                    "Connection accepted", viewer.getName() + " accepted your connection request", viewerId, viewerId);
            return new ConnectResult("CONNECTED", viewer.getConnectionsCount());
        }

        // ACCEPTED: viewer is disconnecting.
        connectionRepository.delete(connection);
        viewer.setConnectionsCount(Math.max(0, viewer.getConnectionsCount() - 1));
        target.setConnectionsCount(Math.max(0, target.getConnectionsCount() - 1));
        userRepository.save(viewer);
        userRepository.save(target);
        return new ConnectResult("NONE", viewer.getConnectionsCount());
    }

    @Transactional
    public void declineConnection(String viewerId, String requesterId) {
        if (viewerId.equals(requesterId)) throw new BadRequestException("Cannot decline yourself");
        getEntityOrThrow(requesterId);

        String a = viewerId.compareTo(requesterId) < 0 ? viewerId : requesterId;
        String b = viewerId.compareTo(requesterId) < 0 ? requesterId : viewerId;

        Connection connection = connectionRepository.findByUserAIdAndUserBId(a, b)
                .orElseThrow(() -> new ResourceNotFoundException("No pending connection request from " + requesterId));

        if (connection.getStatus() != Connection.Status.PENDING || !connection.getRequestedBy().equals(requesterId)) {
            throw new BadRequestException("No pending connection request from " + requesterId);
        }

        connectionRepository.delete(connection);
    }

    @Transactional
    public void blockUser(String viewerId, String targetId) {
        if (viewerId.equals(targetId)) throw new BadRequestException("Cannot block yourself");
        User viewer = getEntityOrThrow(viewerId);
        User target = getEntityOrThrow(targetId);

        if (!userBlockRepository.existsByBlockerIdAndBlockedId(viewerId, targetId)) {
            userBlockRepository.save(UserBlock.builder().blockerId(viewerId).blockedId(targetId).build());
        }

        // Blocking also severs any connection between the two.
        String a = viewerId.compareTo(targetId) < 0 ? viewerId : targetId;
        String b = viewerId.compareTo(targetId) < 0 ? targetId : viewerId;
        connectionRepository.findByUserAIdAndUserBId(a, b).ifPresent(connection -> {
            boolean wasAccepted = connection.getStatus() == Connection.Status.ACCEPTED;
            connectionRepository.delete(connection);
            if (wasAccepted) {
                viewer.setConnectionsCount(Math.max(0, viewer.getConnectionsCount() - 1));
                target.setConnectionsCount(Math.max(0, target.getConnectionsCount() - 1));
                userRepository.save(viewer);
                userRepository.save(target);
            }
        });
    }

    @Transactional
    public void unblockUser(String viewerId, String targetId) {
        userBlockRepository.deleteByBlockerIdAndBlockedId(viewerId, targetId);
    }

    @Transactional(readOnly = true)
    public List<UserDto> listBlockedUsers(String viewerId) {
        List<String> blockedIds = userBlockRepository.findByBlockerId(viewerId).stream()
                .map(UserBlock::getBlockedId)
                .toList();
        return userRepository.findAllById(blockedIds).stream()
                .map(u -> userMapper.toDto(u, null, null))
                .toList();
    }

    @Transactional
    public void muteUser(String viewerId, String targetId) {
        if (viewerId.equals(targetId)) throw new BadRequestException("Cannot mute yourself");
        getEntityOrThrow(targetId);
        if (!mutedAccountRepository.existsByMuterIdAndMutedId(viewerId, targetId)) {
            mutedAccountRepository.save(MutedAccount.builder().muterId(viewerId).mutedId(targetId).build());
        }
    }

    @Transactional
    public void unmuteUser(String viewerId, String targetId) {
        mutedAccountRepository.deleteByMuterIdAndMutedId(viewerId, targetId);
    }

    @Transactional(readOnly = true)
    public List<UserDto> listMutedUsers(String viewerId) {
        List<String> mutedIds = mutedAccountRepository.findByMuterId(viewerId).stream()
                .map(MutedAccount::getMutedId)
                .toList();
        return userRepository.findAllById(mutedIds).stream()
                .map(u -> userMapper.toDto(u, null, null))
                .toList();
    }

    public record FollowResult(boolean following) {}

    @Transactional
    public FollowResult toggleFollow(String viewerId, String targetId) {
        if (viewerId.equals(targetId)) throw new BadRequestException("Cannot follow yourself");
        getEntityOrThrow(targetId);

        if (userFollowRepository.existsByFollowerIdAndFolloweeId(viewerId, targetId)) {
            userFollowRepository.deleteByFollowerIdAndFolloweeId(viewerId, targetId);
            return new FollowResult(false);
        } else {
            userFollowRepository.save(UserFollow.builder().followerId(viewerId).followeeId(targetId).build());
            return new FollowResult(true);
        }
    }

    public record MutualConnectionsResult(List<UserDto> users, int totalCount) {}

    @Transactional(readOnly = true)
    public MutualConnectionsResult getMutualConnections(String viewerId, String targetId, int limit) {
        Set<String> viewerPartners = new LinkedHashSet<>();
        for (Connection c : connectionRepository.findAcceptedConnections(viewerId)) {
            viewerPartners.add(c.getUserAId().equals(viewerId) ? c.getUserBId() : c.getUserAId());
        }
        Set<String> targetPartners = new java.util.HashSet<>();
        for (Connection c : connectionRepository.findAcceptedConnections(targetId)) {
            targetPartners.add(c.getUserAId().equals(targetId) ? c.getUserBId() : c.getUserAId());
        }
        viewerPartners.retainAll(targetPartners);
        int total = viewerPartners.size();
        List<String> capped = viewerPartners.stream().limit(Math.max(limit, 1)).toList();
        List<UserDto> users = userRepository.findAllById(capped).stream().map(u -> userMapper.toDto(u, null, null)).toList();
        return new MutualConnectionsResult(users, total);
    }

    @Transactional(readOnly = true)
    public List<UserDto> listUserConnections(String viewerId, String targetUserId) {
        getEntityOrThrow(targetUserId);
        List<Connection> connections = connectionRepository.findAcceptedConnections(targetUserId);
        if (connections.isEmpty()) {
            return List.of();
        }
        List<String> partnerIds = connections.stream()
                .map(c -> c.getUserAId().equals(targetUserId) ? c.getUserBId() : c.getUserAId())
                .toList();
        List<User> partnerUsers = userRepository.findAllById(partnerIds);
        var statusMap = bulkResolveConnectionStatuses(viewerId, partnerUsers);
        return partnerUsers.stream()
                .map(u -> userMapper.toDto(u, statusMap.getOrDefault(u.getId(), "NONE"), null))
                .toList();
    }

    // ---- Experience ----

    @Transactional(readOnly = true)
    public List<ExperienceDto> listExperiences(String userId) {
        return userExperienceRepository.findByUser_IdOrderBySortOrderAsc(userId).stream().map(userMapper::toDto).toList();
    }

    @Transactional
    public ExperienceDto addExperience(String userId, UpsertExperienceRequest request) {
        User user = getEntityOrThrow(userId);
        int nextOrder = userExperienceRepository.findByUser_IdOrderBySortOrderAsc(userId).size();
        UserExperience saved = userExperienceRepository.save(UserExperience.builder()
                .user(user).company(request.company()).role(request.role()).employmentType(request.employmentType())
                .location(request.location()).startDate(request.startDate()).endDate(request.endDate())
                .isCurrent(request.isCurrent()).description(request.description()).companyUrl(request.companyUrl())
                .sortOrder(nextOrder).build());
        return userMapper.toDto(saved);
    }

    @Transactional
    public ExperienceDto updateExperience(String userId, String id, UpsertExperienceRequest request) {
        UserExperience e = userExperienceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Experience not found: " + id));
        if (!e.getUser().getId().equals(userId)) throw new ForbiddenException("You can only edit your own experience");
        e.setCompany(request.company());
        e.setRole(request.role());
        e.setEmploymentType(request.employmentType());
        e.setLocation(request.location());
        e.setStartDate(request.startDate());
        e.setEndDate(request.endDate());
        e.setCurrent(request.isCurrent());
        e.setDescription(request.description());
        e.setCompanyUrl(request.companyUrl());
        return userMapper.toDto(userExperienceRepository.save(e));
    }

    @Transactional
    public void deleteExperience(String userId, String id) {
        UserExperience e = userExperienceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Experience not found: " + id));
        if (!e.getUser().getId().equals(userId)) throw new ForbiddenException("You can only delete your own experience");
        userExperienceRepository.delete(e);
    }

    // ---- Education ----

    @Transactional(readOnly = true)
    public List<EducationDto> listEducation(String userId) {
        return userEducationRepository.findByUser_IdOrderBySortOrderAsc(userId).stream().map(userMapper::toDto).toList();
    }

    @Transactional
    public EducationDto addEducation(String userId, UpsertEducationRequest request) {
        User user = getEntityOrThrow(userId);
        int nextOrder = userEducationRepository.findByUser_IdOrderBySortOrderAsc(userId).size();
        UserEducation saved = userEducationRepository.save(UserEducation.builder()
                .user(user).institution(request.institution()).degree(request.degree()).fieldOfStudy(request.fieldOfStudy())
                .startYear(request.startYear()).endYear(request.endYear()).grade(request.grade())
                .description(request.description()).sortOrder(nextOrder).build());
        return userMapper.toDto(saved);
    }

    @Transactional
    public EducationDto updateEducation(String userId, String id, UpsertEducationRequest request) {
        UserEducation e = userEducationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Education not found: " + id));
        if (!e.getUser().getId().equals(userId)) throw new ForbiddenException("You can only edit your own education");
        e.setInstitution(request.institution());
        e.setDegree(request.degree());
        e.setFieldOfStudy(request.fieldOfStudy());
        e.setStartYear(request.startYear());
        e.setEndYear(request.endYear());
        e.setGrade(request.grade());
        e.setDescription(request.description());
        return userMapper.toDto(userEducationRepository.save(e));
    }

    @Transactional
    public void deleteEducation(String userId, String id) {
        UserEducation e = userEducationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Education not found: " + id));
        if (!e.getUser().getId().equals(userId)) throw new ForbiddenException("You can only delete your own education");
        userEducationRepository.delete(e);
    }

    // ---- Achievements ----

    @Transactional(readOnly = true)
    public List<AchievementDto> listAchievements(String userId) {
        return userAchievementRepository.findByUser_IdOrderBySortOrderAsc(userId).stream().map(userMapper::toDto).toList();
    }

    @Transactional
    public AchievementDto addAchievement(String userId, UpsertAchievementRequest request) {
        User user = getEntityOrThrow(userId);
        int nextOrder = userAchievementRepository.findByUser_IdOrderBySortOrderAsc(userId).size();
        UserAchievement saved = userAchievementRepository.save(UserAchievement.builder()
                .user(user).title(request.title()).organization(request.organization()).achievedOn(request.achievedOn())
                .description(request.description()).credentialUrl(request.credentialUrl()).sortOrder(nextOrder).build());
        return userMapper.toDto(saved);
    }

    @Transactional
    public AchievementDto updateAchievement(String userId, String id, UpsertAchievementRequest request) {
        UserAchievement a = userAchievementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achievement not found: " + id));
        if (!a.getUser().getId().equals(userId)) throw new ForbiddenException("You can only edit your own achievements");
        a.setTitle(request.title());
        a.setOrganization(request.organization());
        a.setAchievedOn(request.achievedOn());
        a.setDescription(request.description());
        a.setCredentialUrl(request.credentialUrl());
        return userMapper.toDto(userAchievementRepository.save(a));
    }

    @Transactional
    public void deleteAchievement(String userId, String id) {
        UserAchievement a = userAchievementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achievement not found: " + id));
        if (!a.getUser().getId().equals(userId)) throw new ForbiddenException("You can only delete your own achievements");
        userAchievementRepository.delete(a);
    }

    // ---- Certifications ----

    @Transactional(readOnly = true)
    public List<CertificationDto> listCertifications(String userId) {
        return userCertificationRepository.findByUser_IdOrderBySortOrderAsc(userId).stream().map(userMapper::toDto).toList();
    }

    @Transactional
    public CertificationDto addCertification(String userId, UpsertCertificationRequest request) {
        User user = getEntityOrThrow(userId);
        int nextOrder = userCertificationRepository.findByUser_IdOrderBySortOrderAsc(userId).size();
        UserCertification saved = userCertificationRepository.save(UserCertification.builder()
                .user(user).title(request.title()).issuingOrg(request.issuingOrg()).issueDate(request.issueDate())
                .expiryDate(request.expiryDate()).credentialId(request.credentialId()).credentialUrl(request.credentialUrl())
                .sortOrder(nextOrder).build());
        return userMapper.toDto(saved);
    }

    @Transactional
    public CertificationDto updateCertification(String userId, String id, UpsertCertificationRequest request) {
        UserCertification c = userCertificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certification not found: " + id));
        if (!c.getUser().getId().equals(userId)) throw new ForbiddenException("You can only edit your own certifications");
        c.setTitle(request.title());
        c.setIssuingOrg(request.issuingOrg());
        c.setIssueDate(request.issueDate());
        c.setExpiryDate(request.expiryDate());
        c.setCredentialId(request.credentialId());
        c.setCredentialUrl(request.credentialUrl());
        return userMapper.toDto(userCertificationRepository.save(c));
    }

    @Transactional
    public void deleteCertification(String userId, String id) {
        UserCertification c = userCertificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certification not found: " + id));
        if (!c.getUser().getId().equals(userId)) throw new ForbiddenException("You can only delete your own certifications");
        userCertificationRepository.delete(c);
    }

    // ---- Publications ----

    @Transactional(readOnly = true)
    public List<PublicationDto> listPublications(String userId) {
        return userPublicationRepository.findByUser_IdOrderBySortOrderAsc(userId).stream().map(userMapper::toDto).toList();
    }

    @Transactional
    public PublicationDto addPublication(String userId, UpsertPublicationRequest request) {
        User user = getEntityOrThrow(userId);
        int nextOrder = userPublicationRepository.findByUser_IdOrderBySortOrderAsc(userId).size();
        UserPublication saved = userPublicationRepository.save(UserPublication.builder()
                .user(user).title(request.title()).publisher(request.publisher()).publishDate(request.publishDate())
                .description(request.description()).url(request.url()).sortOrder(nextOrder).build());
        return userMapper.toDto(saved);
    }

    @Transactional
    public PublicationDto updatePublication(String userId, String id, UpsertPublicationRequest request) {
        UserPublication p = userPublicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Publication not found: " + id));
        if (!p.getUser().getId().equals(userId)) throw new ForbiddenException("You can only edit your own publications");
        p.setTitle(request.title());
        p.setPublisher(request.publisher());
        p.setPublishDate(request.publishDate());
        p.setDescription(request.description());
        p.setUrl(request.url());
        return userMapper.toDto(userPublicationRepository.save(p));
    }

    @Transactional
    public void deletePublication(String userId, String id) {
        UserPublication p = userPublicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Publication not found: " + id));
        if (!p.getUser().getId().equals(userId)) throw new ForbiddenException("You can only delete your own publications");
        userPublicationRepository.delete(p);
    }

    // ---- Projects ----

    @Transactional(readOnly = true)
    public List<ProjectDto> listProjects(String userId) {
        return userProjectRepository.findByUser_IdOrderBySortOrderAsc(userId).stream().map(userMapper::toDto).toList();
    }

    @Transactional
    public ProjectDto addProject(String userId, UpsertProjectRequest request) {
        User user = getEntityOrThrow(userId);
        int nextOrder = userProjectRepository.findByUser_IdOrderBySortOrderAsc(userId).size();
        UserProject saved = userProjectRepository.save(UserProject.builder()
                .user(user).title(request.title()).description(request.description())
                .technologies(UserMapper.joinTechnologies(request.technologies()))
                .imageUrl(request.imageUrl()).githubUrl(request.githubUrl()).liveUrl(request.liveUrl())
                .startDate(request.startDate()).endDate(request.endDate())
                .projectType(parseProjectType(request.projectType()))
                .sortOrder(nextOrder).build());
        return userMapper.toDto(saved);
    }

    @Transactional
    public ProjectDto updateProject(String userId, String id, UpsertProjectRequest request) {
        UserProject p = userProjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        if (!p.getUser().getId().equals(userId)) throw new ForbiddenException("You can only edit your own projects");
        p.setTitle(request.title());
        p.setDescription(request.description());
        p.setTechnologies(UserMapper.joinTechnologies(request.technologies()));
        p.setImageUrl(request.imageUrl());
        p.setGithubUrl(request.githubUrl());
        p.setLiveUrl(request.liveUrl());
        p.setStartDate(request.startDate());
        p.setEndDate(request.endDate());
        p.setProjectType(parseProjectType(request.projectType()));
        return userMapper.toDto(userProjectRepository.save(p));
    }

    @Transactional
    public void deleteProject(String userId, String id) {
        UserProject p = userProjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        if (!p.getUser().getId().equals(userId)) throw new ForbiddenException("You can only delete your own projects");
        userProjectRepository.delete(p);
    }

    private UserProject.Type parseProjectType(String type) {
        if (type == null || type.isBlank()) return UserProject.Type.PERSONAL;
        try {
            return UserProject.Type.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid project type: " + type);
        }
    }
}
