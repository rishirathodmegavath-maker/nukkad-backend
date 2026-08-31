package com.nukkad.user.controller;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.response.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import com.nukkad.common.response.PageResponse;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.user.dto.AchievementDto;
import com.nukkad.user.dto.CertificationDto;
import com.nukkad.user.dto.EducationDto;
import com.nukkad.user.dto.ExperienceDto;
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
import com.nukkad.user.dto.WriteRecommendationRequest;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationPreferenceService;
import com.nukkad.user.dto.AccountPrivacyDto;
import com.nukkad.user.dto.AppearanceSettingsDto;
import com.nukkad.user.dto.UpdateAccountPrivacyRequest;
import com.nukkad.user.dto.UpdateAppearanceSettingsRequest;
import com.nukkad.user.entity.ConnectPermission;
import com.nukkad.user.entity.MessagePermission;
import com.nukkad.user.entity.ProfileSection;
import com.nukkad.user.entity.ProfileVisibility;
import com.nukkad.user.entity.SectionVisibility;
import com.nukkad.user.entity.ThemeMode;
import com.nukkad.user.entity.ThemePreset;
import com.nukkad.user.entity.UserAppearanceSettings;
import com.nukkad.user.entity.UserPrivacySettings;
import com.nukkad.user.service.ProfilePrivacyService;
import com.nukkad.user.service.UserAppearanceSettingsService;
import com.nukkad.user.service.UserPrivacySettingsService;
import com.nukkad.user.service.UserEndorsementService;
import com.nukkad.user.service.UserRecommendationService;
import com.nukkad.user.service.UserService;
import com.nukkad.matching.dto.CofounderMatchDto;
import com.nukkad.matching.dto.RecommendedUserDto;
import com.nukkad.matching.service.CompatibilityService;
import com.nukkad.matching.service.PeopleRecommendationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final UserEndorsementService userEndorsementService;
    private final UserRecommendationService userRecommendationService;
    private final ProfilePrivacyService profilePrivacyService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final UserPrivacySettingsService userPrivacySettingsService;
    private final UserAppearanceSettingsService userAppearanceSettingsService;
    private final PeopleRecommendationService peopleRecommendationService;
    private final CompatibilityService compatibilityService;

    public UserController(UserService userService, UserEndorsementService userEndorsementService,
                           UserRecommendationService userRecommendationService, ProfilePrivacyService profilePrivacyService,
                           NotificationPreferenceService notificationPreferenceService,
                           UserPrivacySettingsService userPrivacySettingsService,
                           UserAppearanceSettingsService userAppearanceSettingsService,
                           PeopleRecommendationService peopleRecommendationService,
                           CompatibilityService compatibilityService) {
        this.peopleRecommendationService = peopleRecommendationService;
        this.compatibilityService = compatibilityService;
        this.userService = userService;
        this.userEndorsementService = userEndorsementService;
        this.userRecommendationService = userRecommendationService;
        this.profilePrivacyService = profilePrivacyService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.userPrivacySettingsService = userPrivacySettingsService;
        this.userAppearanceSettingsService = userAppearanceSettingsService;
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.getCurrentUser(principal.id()));
    }

    @PatchMapping("/me")
    public ApiResponse<UserDto> updateMe(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userService.updateCurrentUser(principal.id(), request));
    }

    @PostMapping("/me/avatar")
    public ApiResponse<UserDto> uploadAvatar(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(userService.updateAvatar(principal.id(), file));
    }

    @DeleteMapping("/me/avatar")
    public ApiResponse<UserDto> removeAvatar(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.removeAvatar(principal.id()));
    }

    @PostMapping("/me/cover")
    public ApiResponse<UserDto> uploadCover(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(userService.updateCover(principal.id(), file));
    }

    @DeleteMapping("/me/cover")
    public ApiResponse<UserDto> removeCover(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.removeCover(principal.id()));
    }

    @GetMapping("/suggested")
    public ApiResponse<java.util.List<UserDto>> suggested(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(userService.listSuggested(principal.id(), limit));
    }

    /** "People you may know" — graph-based (BFS over accepted connections) + weighted content scoring. See PeopleRecommendationService. */
    @GetMapping("/me/recommendations")
    public ApiResponse<List<RecommendedUserDto>> recommendations(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                    @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(peopleRecommendationService.recommend(principal.id(), limit));
    }

    /** Co-founder / collaborator compatibility — complementary skills, not just similar ones. See CompatibilityService. */
    @GetMapping("/me/matches")
    public ApiResponse<List<CofounderMatchDto>> matches(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(compatibilityService.findMatches(principal.id(), limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDto> getUser(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(userService.getUser(id, principal.id()));
    }

    @GetMapping
    public ApiResponse<PageResponse<UserDto>> listUsers(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(required = false) String skill,
                                                          @RequestParam(required = false) String collegeOrCompany,
                                                          @RequestParam(required = false) String location,
                                                          @RequestParam(required = false) String role,
                                                          @RequestParam(required = false) String lookingFor,
                                                          @RequestParam(required = false) Integer minExperience,
                                                          @RequestParam(required = false) String chapterId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        var result = userService.listUsers(principal.id(), q, skill, collegeOrCompany, location, role,
                lookingFor, minExperience, chapterId, page, size);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @PostMapping("/{id}/connect")
    public ApiResponse<Map<String, Object>> connect(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        var result = userService.toggleConnect(principal.id(), id);
        return ApiResponse.ok(Map.of("status", result.status(), "connectionsCount", result.connectionsCount()));
    }

    @PostMapping("/{id}/connect/decline")
    public ApiResponse<Void> declineConnect(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userService.declineConnection(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/follow")
    public ApiResponse<Map<String, Object>> follow(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        try {
            var result = userService.toggleFollow(principal.id(), id);
            return ApiResponse.ok(Map.of("following", result.following()));
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous follow-toggle requests both saw "not following yet" and raced to
            // insert; the loser hits the (follower_id, followee_id) unique key. The transaction
            // that failed has already rolled back cleanly by the time it reaches here — the
            // desired end state (following) is true regardless of which request "won".
            return ApiResponse.ok(Map.of("following", true));
        }
    }

    @PostMapping("/{id}/endorsements/{skill}")
    public ApiResponse<Map<String, Object>> toggleEndorsement(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @PathVariable String id, @PathVariable String skill) {
        var result = userEndorsementService.toggle(principal.id(), id, skill);
        return ApiResponse.ok(Map.of("endorsed", result.endorsed(), "count", result.count()));
    }

    // ---- Recommendations ----

    @PostMapping("/{id}/recommendations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecommendationDto> writeRecommendation(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @PathVariable String id,
                                                                @Valid @RequestBody WriteRecommendationRequest request) {
        return ApiResponse.ok(userRecommendationService.write(principal.id(), id, request));
    }

    @GetMapping("/me/recommendations/pending")
    public ApiResponse<List<RecommendationDto>> pendingRecommendations(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userRecommendationService.listPendingForSubject(principal.id()));
    }

    @PostMapping("/me/recommendations/{id}/approve")
    public ApiResponse<RecommendationDto> approveRecommendation(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(userRecommendationService.approve(principal.id(), id));
    }

    @PostMapping("/me/recommendations/{id}/reject")
    public ApiResponse<RecommendationDto> rejectRecommendation(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(userRecommendationService.reject(principal.id(), id));
    }

    @DeleteMapping("/me/recommendations/authored/{id}")
    public ApiResponse<Void> deleteAuthoredRecommendation(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userRecommendationService.deleteAuthored(principal.id(), id);
        return ApiResponse.ok(null);
    }

    // ---- Privacy ----

    @GetMapping("/me/privacy")
    public ApiResponse<Map<String, String>> getPrivacySettings(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(toStringMap(profilePrivacyService.getSettings(principal.id())));
    }

    @PatchMapping("/me/privacy")
    public ApiResponse<Map<String, String>> updatePrivacySettings(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                    @RequestBody Map<String, String> updates) {
        Map<ProfileSection, SectionVisibility> parsed = new EnumMap<>(ProfileSection.class);
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            try {
                parsed.put(ProfileSection.valueOf(entry.getKey()), SectionVisibility.valueOf(entry.getValue()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid privacy setting: " + entry.getKey() + "=" + entry.getValue());
            }
        }
        return ApiResponse.ok(toStringMap(profilePrivacyService.updateSettings(principal.id(), parsed)));
    }

    private Map<String, String> toStringMap(Map<ProfileSection, SectionVisibility> settings) {
        return settings.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), e -> e.getValue().name()));
    }

    // ---- Notification preferences ----

    @GetMapping("/me/notification-preferences")
    public ApiResponse<Map<String, Boolean>> getNotificationPreferences(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(notificationPreferenceService.getPreferences(principal.id()).entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)));
    }

    @PatchMapping("/me/notification-preferences")
    public ApiResponse<Map<String, Boolean>> updateNotificationPreferences(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                             @RequestBody Map<String, Boolean> updates) {
        Map<NotificationType, Boolean> parsed = new EnumMap<>(NotificationType.class);
        for (Map.Entry<String, Boolean> entry : updates.entrySet()) {
            try {
                parsed.put(NotificationType.valueOf(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid notification type: " + entry.getKey());
            }
        }
        notificationPreferenceService.updatePreferences(principal.id(), parsed);
        return ApiResponse.ok(notificationPreferenceService.getPreferences(principal.id()).entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)));
    }

    // ---- Account privacy (message/connect/profile visibility) ----

    @GetMapping("/me/account-privacy")
    public ApiResponse<AccountPrivacyDto> getAccountPrivacy(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(toAccountPrivacyDto(userPrivacySettingsService.getSettings(principal.id())));
    }

    @PatchMapping("/me/account-privacy")
    public ApiResponse<AccountPrivacyDto> updateAccountPrivacy(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @RequestBody UpdateAccountPrivacyRequest request) {
        try {
            ProfileVisibility profileVisibility = request.profileVisibility() == null ? null : ProfileVisibility.valueOf(request.profileVisibility());
            MessagePermission messagePermission = request.messagePermission() == null ? null : MessagePermission.valueOf(request.messagePermission());
            ConnectPermission connectPermission = request.connectPermission() == null ? null : ConnectPermission.valueOf(request.connectPermission());
            UserPrivacySettings updated = userPrivacySettingsService.updateSettings(
                    principal.id(), profileVisibility, messagePermission, connectPermission);
            return ApiResponse.ok(toAccountPrivacyDto(updated));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid account privacy value");
        }
    }

    private AccountPrivacyDto toAccountPrivacyDto(UserPrivacySettings settings) {
        return new AccountPrivacyDto(
                settings.getProfileVisibility().name(),
                settings.getMessagePermission().name(),
                settings.getConnectPermission().name());
    }

    // ---- Appearance (theme mode, preset, custom colour, advanced overrides) ----

    @GetMapping("/me/appearance")
    public ApiResponse<AppearanceSettingsDto> getAppearanceSettings(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(toAppearanceSettingsDto(userAppearanceSettingsService.getSettings(principal.id())));
    }

    @PatchMapping("/me/appearance")
    public ApiResponse<AppearanceSettingsDto> updateAppearanceSettings(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                        @RequestBody UpdateAppearanceSettingsRequest request) {
        try {
            ThemeMode themeMode = request.themeMode() == null ? null : ThemeMode.valueOf(request.themeMode());
            ThemePreset themePreset = request.themePreset() == null ? null : ThemePreset.valueOf(request.themePreset());
            UserAppearanceSettings updated = Boolean.TRUE.equals(request.resetToDefault())
                    ? userAppearanceSettingsService.resetToDefault(principal.id(), themeMode, themePreset)
                    : Boolean.TRUE.equals(request.clearAdvancedOverrides())
                    ? userAppearanceSettingsService.clearAdvancedOverrides(principal.id())
                    : userAppearanceSettingsService.updateSettings(
                            principal.id(), themeMode, themePreset, request.customPrimaryColor(),
                            request.sidebarColor(), request.pageBgColor(), request.cardBgColor(),
                            request.headerBgColor(), request.borderColor(), request.secondarySurfaceColor());
            return ApiResponse.ok(toAppearanceSettingsDto(updated));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid appearance setting value");
        }
    }

    private AppearanceSettingsDto toAppearanceSettingsDto(UserAppearanceSettings settings) {
        return new AppearanceSettingsDto(
                settings.getThemeMode().name(),
                settings.getThemePreset().name(),
                settings.getCustomPrimaryColor(),
                settings.getSidebarColor(),
                settings.getPageBgColor(),
                settings.getCardBgColor(),
                settings.getHeaderBgColor(),
                settings.getBorderColor(),
                settings.getSecondarySurfaceColor());
    }

    @PostMapping("/{id}/block")
    public ApiResponse<Void> block(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userService.blockUser(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/unblock")
    public ApiResponse<Void> unblock(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userService.unblockUser(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me/blocked")
    public ApiResponse<java.util.List<UserDto>> blocked(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.listBlockedUsers(principal.id()));
    }

    @PostMapping("/{id}/mute")
    public ApiResponse<Void> mute(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userService.muteUser(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/unmute")
    public ApiResponse<Void> unmute(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userService.unmuteUser(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me/muted")
    public ApiResponse<java.util.List<UserDto>> muted(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.listMutedUsers(principal.id()));
    }

    @GetMapping("/{id}/mutual-connections")
    public ApiResponse<Map<String, Object>> mutualConnections(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @PathVariable String id,
                                                                @RequestParam(defaultValue = "6") int limit) {
        var result = userService.getMutualConnections(principal.id(), id, limit);
        return ApiResponse.ok(Map.of("users", result.users(), "totalCount", result.totalCount()));
    }

    @GetMapping("/{id}/connections")
    public ApiResponse<List<UserDto>> listUserConnections(@AuthenticationPrincipal AuthenticatedUser principal,
                                                           @PathVariable String id) {
        return ApiResponse.ok(userService.listUserConnections(principal.id(), id));
    }

    // ---- Experience ----

    @GetMapping("/me/experiences")
    public ApiResponse<List<ExperienceDto>> listExperiences(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.listExperiences(principal.id()));
    }

    @PostMapping("/me/experiences")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExperienceDto> addExperience(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @Valid @RequestBody UpsertExperienceRequest request) {
        return ApiResponse.ok(userService.addExperience(principal.id(), request));
    }

    @PatchMapping("/me/experiences/{id}")
    public ApiResponse<ExperienceDto> updateExperience(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                                         @Valid @RequestBody UpsertExperienceRequest request) {
        return ApiResponse.ok(userService.updateExperience(principal.id(), id, request));
    }

    @DeleteMapping("/me/experiences/{id}")
    public ApiResponse<Void> deleteExperience(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userService.deleteExperience(principal.id(), id);
        return ApiResponse.ok(null);
    }

    // ---- Education ----

    @GetMapping("/me/education")
    public ApiResponse<List<EducationDto>> listEducation(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.listEducation(principal.id()));
    }

    @PostMapping("/me/education")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EducationDto> addEducation(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody UpsertEducationRequest request) {
        return ApiResponse.ok(userService.addEducation(principal.id(), request));
    }

    @PatchMapping("/me/education/{id}")
    public ApiResponse<EducationDto> updateEducation(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                                       @Valid @RequestBody UpsertEducationRequest request) {
        return ApiResponse.ok(userService.updateEducation(principal.id(), id, request));
    }

    @DeleteMapping("/me/education/{id}")
    public ApiResponse<Void> deleteEducation(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userService.deleteEducation(principal.id(), id);
        return ApiResponse.ok(null);
    }

    // ---- Achievements ----

    @GetMapping("/me/achievements")
    public ApiResponse<List<AchievementDto>> listAchievements(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.listAchievements(principal.id()));
    }

    @PostMapping("/me/achievements")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AchievementDto> addAchievement(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @Valid @RequestBody UpsertAchievementRequest request) {
        return ApiResponse.ok(userService.addAchievement(principal.id(), request));
    }

    @PatchMapping("/me/achievements/{id}")
    public ApiResponse<AchievementDto> updateAchievement(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                                           @Valid @RequestBody UpsertAchievementRequest request) {
        return ApiResponse.ok(userService.updateAchievement(principal.id(), id, request));
    }

    @DeleteMapping("/me/achievements/{id}")
    public ApiResponse<Void> deleteAchievement(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userService.deleteAchievement(principal.id(), id);
        return ApiResponse.ok(null);
    }

    // ---- Certifications ----

    @GetMapping("/me/certifications")
    public ApiResponse<List<CertificationDto>> listCertifications(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.listCertifications(principal.id()));
    }

    @PostMapping("/me/certifications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CertificationDto> addCertification(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @Valid @RequestBody UpsertCertificationRequest request) {
        return ApiResponse.ok(userService.addCertification(principal.id(), request));
    }

    @PatchMapping("/me/certifications/{id}")
    public ApiResponse<CertificationDto> updateCertification(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                                               @Valid @RequestBody UpsertCertificationRequest request) {
        return ApiResponse.ok(userService.updateCertification(principal.id(), id, request));
    }

    @DeleteMapping("/me/certifications/{id}")
    public ApiResponse<Void> deleteCertification(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userService.deleteCertification(principal.id(), id);
        return ApiResponse.ok(null);
    }

    // ---- Publications ----

    @GetMapping("/me/publications")
    public ApiResponse<List<PublicationDto>> listPublications(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.listPublications(principal.id()));
    }

    @PostMapping("/me/publications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PublicationDto> addPublication(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @Valid @RequestBody UpsertPublicationRequest request) {
        return ApiResponse.ok(userService.addPublication(principal.id(), request));
    }

    @PatchMapping("/me/publications/{id}")
    public ApiResponse<PublicationDto> updatePublication(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                                           @Valid @RequestBody UpsertPublicationRequest request) {
        return ApiResponse.ok(userService.updatePublication(principal.id(), id, request));
    }

    @DeleteMapping("/me/publications/{id}")
    public ApiResponse<Void> deletePublication(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userService.deletePublication(principal.id(), id);
        return ApiResponse.ok(null);
    }

    // ---- Projects ----

    @GetMapping("/me/projects")
    public ApiResponse<List<ProjectDto>> listProjects(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.listProjects(principal.id()));
    }

    @PostMapping("/me/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectDto> addProject(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @Valid @RequestBody UpsertProjectRequest request) {
        return ApiResponse.ok(userService.addProject(principal.id(), request));
    }

    @PatchMapping("/me/projects/{id}")
    public ApiResponse<ProjectDto> updateProject(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
                                                  @Valid @RequestBody UpsertProjectRequest request) {
        return ApiResponse.ok(userService.updateProject(principal.id(), id, request));
    }

    @DeleteMapping("/me/projects/{id}")
    public ApiResponse<Void> deleteProject(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        userService.deleteProject(principal.id(), id);
        return ApiResponse.ok(null);
    }
}
