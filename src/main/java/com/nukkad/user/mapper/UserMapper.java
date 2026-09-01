package com.nukkad.user.mapper;

import com.nukkad.user.dto.AchievementDto;
import com.nukkad.user.dto.CertificationDto;
import com.nukkad.user.dto.EducationDto;
import com.nukkad.user.dto.EndorsementSummaryDto;
import com.nukkad.user.dto.ExperienceDto;
import com.nukkad.user.dto.ProfileSections;
import com.nukkad.user.dto.ProjectDto;
import com.nukkad.user.dto.PublicationDto;
import com.nukkad.user.dto.RecommendationDto;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.LookingFor;
import com.nukkad.user.entity.OpenTo;
import com.nukkad.user.entity.SocialPlatform;
import com.nukkad.user.entity.User;
import com.nukkad.user.entity.UserAchievement;
import com.nukkad.user.entity.UserCertification;
import com.nukkad.user.entity.UserEducation;
import com.nukkad.user.entity.UserExperience;
import com.nukkad.user.entity.UserProject;
import com.nukkad.user.entity.UserPublication;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    private static final long ONLINE_WINDOW_MINUTES = 15;

    public UserDto toDto(User user) {
        return toDto(user, null, null);
    }

    /** List-view mapper: cheap fields only (no per-row sub-entity queries, no completeness score, no endorsements/recommendations). */
    public UserDto toDto(User user, String connectionStatus, Boolean isFollowing) {
        return toDto(user, connectionStatus, isFollowing, ProfileSections.empty(), null, List.of(), List.of(), true);
    }

    /** Single-profile mapper: carries the full portfolio, completeness score, endorsement summary, public recommendations,
     *  and whether social links should be shown (privacy-gated by the caller — this class stays privacy-agnostic otherwise,
     *  since section-level hide/show is done by UserService substituting empty lists before calling this). */
    public UserDto toDto(User user, String connectionStatus, Boolean isFollowing, ProfileSections sections,
                          Integer profileCompleteness, List<EndorsementSummaryDto> endorsementSummary,
                          List<RecommendationDto> recommendations, boolean socialLinksVisible) {
        return build(user, connectionStatus, isFollowing,
                sections.experiences().stream().map(this::toDto).toList(),
                sections.education().stream().map(this::toDto).toList(),
                sections.achievements().stream().map(this::toDto).toList(),
                sections.projects().stream().map(this::toDto).toList(),
                sections.certifications().stream().map(this::toDto).toList(),
                sections.publications().stream().map(this::toDto).toList(),
                profileCompleteness, endorsementSummary, recommendations, socialLinksVisible);
    }

    private UserDto build(User user, String connectionStatus, Boolean isFollowing,
                           List<ExperienceDto> experiences, List<EducationDto> education,
                           List<AchievementDto> achievements, List<ProjectDto> projects,
                           List<CertificationDto> certifications, List<PublicationDto> publications,
                           Integer profileCompleteness, List<EndorsementSummaryDto> endorsementSummary,
                           List<RecommendationDto> recommendations, boolean socialLinksVisible) {
        boolean online = user.getLastActiveAt() != null
                && user.getLastActiveAt().isAfter(Instant.now().minus(ONLINE_WINDOW_MINUTES, ChronoUnit.MINUTES));

        Map<String, String> socialLinks = !socialLinksVisible ? Map.of() : user.getSocialLinks().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name().toLowerCase(), Map.Entry::getValue));

        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getCoverUrl(),
                user.getHeadline(),
                user.getRole(),
                user.getCollegeOrCompany(),
                user.getLocation(),
                user.getExperienceYears(),
                new HashSet<>(user.getSkills()),
                user.getLookingFor().stream().map(LookingFor::getLabel).collect(Collectors.toSet()),
                user.getOpenTo().stream().map(OpenTo::getLabel).collect(Collectors.toSet()),
                socialLinks,
                user.getGoals(),
                user.getBio(),
                user.getAvailability() == null ? null : user.getAvailability().getLabel(),
                user.getChapterId(),
                user.getConnectionsCount(),
                online,
                user.getCreatedAt(),
                connectionStatus,
                isFollowing,
                experiences,
                education,
                achievements,
                projects,
                certifications,
                publications,
                profileCompleteness,
                endorsementSummary,
                recommendations,
                user.getSecurityRoles().stream().map(Enum::name).collect(Collectors.toSet()),
                user.getGoogleSubject() != null
        );
    }

    /**
     * Profile-visibility-restricted view: the viewer isn't connected and the owner has set their
     * profile to connections-only. Keeps only what's needed for an identity card (name, avatar,
     * headline, connection state) — every array/map field is empty rather than null so the
     * frontend's unconditional {@code .map()} calls on those fields don't throw.
     */
    public UserDto toRestrictedDto(User user, String connectionStatus, Boolean isFollowing) {
        boolean online = user.getLastActiveAt() != null
                && user.getLastActiveAt().isAfter(Instant.now().minus(ONLINE_WINDOW_MINUTES, ChronoUnit.MINUTES));
        return new UserDto(
                user.getId(), user.getName(), null, user.getAvatarUrl(), null, user.getHeadline(),
                null, null, null, 0, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), Map.of(),
                null, null, null, null, user.getConnectionsCount(), online, user.getCreatedAt(),
                connectionStatus, isFollowing, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, List.of(), List.of(), java.util.Set.of(), user.getGoogleSubject() != null
        );
    }

    public ExperienceDto toDto(UserExperience e) {
        return new ExperienceDto(e.getId(), e.getCompany(), e.getRole(), e.getEmploymentType(), e.getLocation(),
                e.getStartDate(), e.getEndDate(), e.isCurrent(), e.getDescription(), e.getCompanyUrl(), e.getSortOrder());
    }

    public EducationDto toDto(UserEducation e) {
        return new EducationDto(e.getId(), e.getInstitution(), e.getDegree(), e.getFieldOfStudy(),
                e.getStartYear(), e.getEndYear(), e.getGrade(), e.getDescription(), e.getSortOrder());
    }

    public AchievementDto toDto(UserAchievement a) {
        return new AchievementDto(a.getId(), a.getTitle(), a.getOrganization(), a.getAchievedOn(),
                a.getDescription(), a.getCredentialUrl(), a.getSortOrder());
    }

    public CertificationDto toDto(UserCertification c) {
        return new CertificationDto(c.getId(), c.getTitle(), c.getIssuingOrg(), c.getIssueDate(), c.getExpiryDate(),
                c.getCredentialId(), c.getCredentialUrl(), c.getSortOrder());
    }

    public PublicationDto toDto(UserPublication p) {
        return new PublicationDto(p.getId(), p.getTitle(), p.getPublisher(), p.getPublishDate(),
                p.getDescription(), p.getUrl(), p.getSortOrder());
    }

    public ProjectDto toDto(UserProject p) {
        List<String> technologies = p.getTechnologies() == null || p.getTechnologies().isBlank()
                ? List.of()
                : Arrays.stream(p.getTechnologies().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        return new ProjectDto(p.getId(), p.getTitle(), p.getDescription(), technologies, p.getImageUrl(),
                p.getGithubUrl(), p.getLiveUrl(), p.getStartDate(), p.getEndDate(), p.getProjectType().name(), p.getSortOrder());
    }

    public static String joinTechnologies(List<String> technologies) {
        if (technologies == null || technologies.isEmpty()) return null;
        return technologies.stream().map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.joining(","));
    }

    public static SocialPlatform parsePlatform(String platform) {
        try {
            return SocialPlatform.valueOf(platform.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid social platform: " + platform);
        }
    }
}
