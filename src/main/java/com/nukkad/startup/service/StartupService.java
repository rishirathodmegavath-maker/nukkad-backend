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
import com.nukkad.startup.dto.StartupMaterialDto;
import com.nukkad.startup.dto.StartupRoleDto;
import com.nukkad.startup.dto.StartupTeamMemberDto;
import com.nukkad.startup.dto.StartupUpdateDto;
import com.nukkad.startup.dto.UpdateStartupRequest;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupFollow;
import com.nukkad.startup.entity.StartupMaterial;
import com.nukkad.startup.entity.StartupMaterialType;
import com.nukkad.startup.entity.StartupRole;
import com.nukkad.startup.entity.StartupRoleType;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.entity.StartupUpdate;
import com.nukkad.startup.entity.StartupVisibility;
import com.nukkad.startup.mapper.StartupMapper;
import com.nukkad.startup.repository.StartupFollowRepository;
import com.nukkad.startup.repository.StartupMaterialRepository;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StartupService {

    private final StartupRepository startupRepository;
    private final StartupTeamMemberRepository teamMemberRepository;
    private final StartupUpdateRepository updateRepository;
    private final StartupRoleRepository roleRepository;
    private final StartupFollowRepository followRepository;
    private final StartupMaterialRepository materialRepository;
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
                           StartupMaterialRepository materialRepository,
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
        this.materialRepository = materialRepository;
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
        requireVisible(startup, viewerId);
        return startupMapper.toDto(startup,
                viewerId != null && followRepository.existsByUserIdAndStartupId(viewerId, id),
                isFounderMember(viewerId, id),
                canViewFundraising(startup, viewerId));
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
                StartupSpecifications.memberId(memberId),
                StartupSpecifications.visibleTo(viewerId != null)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return startupRepository.findAll(spec, pageable)
                .map(s -> startupMapper.toDto(s,
                        viewerId != null && followRepository.existsByUserIdAndStartupId(viewerId, s.getId()),
                        isFounderMember(viewerId, s.getId()),
                        canViewFundraising(s, viewerId)));
    }

    @Transactional(readOnly = true)
    public List<StartupDto> listMyFoundedStartups(String userId) {
        List<String> startupIds = teamMemberRepository.findByUserIdAndIsFounderTrueAndStatus(userId, StartupTeamMember.Status.ACTIVE)
                .stream().map(StartupTeamMember::getStartupId).toList();
        return startupRepository.findAllById(startupIds).stream()
                .map(s -> startupMapper.toDto(s, false, true, true))
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
                .build();
        startup = startupRepository.saveAndFlush(startup);

        teamMemberRepository.save(StartupTeamMember.builder()
                .startupId(startup.getId())
                .userId(creatorId)
                .isFounder(true)
                .status(StartupTeamMember.Status.ACTIVE)
                .build());

        return startupMapper.toDto(startup, false, true, true);
    }

    @Transactional
    public StartupDto updateStartup(String userId, String startupId, UpdateStartupRequest request) {
        Startup startup = getEntityOrThrow(startupId);
        requireFounder(userId, startupId);

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
        if (request.visibility() != null) {
            try {
                startup.setVisibility(StartupVisibility.fromLabel(request.visibility()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown visibility: " + request.visibility());
            }
        }
        if (request.fundraisingVisible() != null) startup.setFundraisingVisible(request.fundraisingVisible());
        if (request.isRaising() != null) startup.setRaising(request.isRaising());
        if (request.stage() != null) startup.setStage(StartupStage.fromLabel(request.stage()));
        if (request.needs() != null) startup.setNeeds(new java.util.HashSet<>(request.needs()));

        Startup saved = startupRepository.saveAndFlush(startup);
        return startupMapper.toDto(saved, false, true, true);
    }

    @Transactional
    public void deleteStartup(String userId, String startupId) {
        getEntityOrThrow(startupId);
        requireFounder(userId, startupId);
        startupRepository.deleteById(startupId);
    }

    @Transactional
    public StartupDto updateLogo(String founderId, String startupId, MultipartFile file) {
        Startup startup = getEntityOrThrow(startupId);
        requireFounder(founderId, startupId);
        startup.setLogoUrl(fileStorageService.storeImage(file, "startup-logos"));
        return startupMapper.toDto(startupRepository.save(startup), false, true, true);
    }

    @Transactional
    public StartupDto removeLogo(String founderId, String startupId) {
        Startup startup = getEntityOrThrow(startupId);
        requireFounder(founderId, startupId);
        startup.setLogoUrl(null);
        return startupMapper.toDto(startupRepository.save(startup), false, true, true);
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

    // ---- Startup materials ----

    @Transactional(readOnly = true)
    public List<StartupMaterialDto> getMaterials(String startupId, String viewerId) {
        getEntityOrThrow(startupId);
        boolean canManage = isFounderMember(viewerId, startupId);
        return materialRepository.findByStartupIdOrderBySortOrderAscCreatedAtAsc(startupId).stream()
                .map(m -> startupMapper.toDto(m, canManage))
                .toList();
    }

    @Transactional
    public StartupMaterialDto addMaterial(String userId, String startupId, String materialTypeLabel,
                                           String title, String url, MultipartFile file) {
        getEntityOrThrow(startupId);
        requireFounder(userId, startupId);
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
            FileStorageService.StoredMedia media = fileStorageService.storeMedia(file, "startup-materials");
            requireExpectedKind(type, media.kind());
            builder.url(media.url())
                    .originalFileName(file.getOriginalFilename())
                    .contentType(file.getContentType());
        }

        StartupMaterial saved = materialRepository.saveAndFlush(builder.build());
        return startupMapper.toDto(saved, true);
    }

    @Transactional
    public StartupMaterialDto updateMaterial(String userId, String materialId, String title, String url, MultipartFile file) {
        StartupMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + materialId));
        requireFounder(userId, material.getStartupId());

        if (title != null) material.setTitle(blankToNull(title));

        if (material.getMaterialType().isExternalLink()) {
            if (url != null) {
                if (url.isBlank()) throw new BadRequestException("URL cannot be blank");
                material.setUrl(normalizeAndValidateUrl(url, material.getMaterialType().getLabel()));
            }
        } else if (file != null && !file.isEmpty()) {
            FileStorageService.StoredMedia media = fileStorageService.storeMedia(file, "startup-materials");
            requireExpectedKind(material.getMaterialType(), media.kind());
            material.setUrl(media.url());
            material.setOriginalFileName(file.getOriginalFilename());
            material.setContentType(file.getContentType());
        }

        return startupMapper.toDto(materialRepository.saveAndFlush(material), true);
    }

    @Transactional
    public void deleteMaterial(String userId, String materialId) {
        StartupMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + materialId));
        requireFounder(userId, material.getStartupId());
        materialRepository.delete(material);
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

    private void requireFounder(String userId, String startupId) {
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

    private void requireTeamMember(String userId, String startupId) {
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

    /** An anonymous caller may only ever reach a PUBLIC startup; never leaks that a member-only
     *  startup exists by returning a different error for that case. */
    private void requireVisible(Startup startup, String viewerId) {
        if (viewerId == null && startup.getVisibility() == StartupVisibility.NUKKAD_MEMBERS) {
            throw new ResourceNotFoundException("Startup not found: " + startup.getId());
        }
    }

    /** Fundraising data is visible to the founder/team of the startup regardless of the toggle,
     *  and to everyone else only when the founder has switched fundraising visibility on. */
    private boolean canViewFundraising(Startup startup, String viewerId) {
        return startup.isFundraisingVisible() || isActiveTeamMember(viewerId, startup.getId());
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
