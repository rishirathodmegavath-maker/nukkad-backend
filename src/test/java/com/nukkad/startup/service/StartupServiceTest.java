package com.nukkad.startup.service;

import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.opportunity.repository.OpportunityRepository;
import com.nukkad.startup.dto.StartupDto;
import com.nukkad.startup.dto.StartupMaterialDto;
import com.nukkad.startup.dto.UpdateStartupRequest;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupMaterial;
import com.nukkad.startup.entity.StartupMaterialType;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.entity.StartupVisibility;
import com.nukkad.startup.mapper.StartupMapper;
import com.nukkad.startup.repository.StartupFollowRepository;
import com.nukkad.startup.repository.StartupMaterialRepository;
import com.nukkad.startup.repository.StartupProfileViewRepository;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupRoleRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import com.nukkad.startup.dto.StartupTeamMemberDto;
import com.nukkad.startup.repository.StartupUpdateRepository;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers Batch 1: visibility enforcement, fundraising-status suppression, rich-profile
 *  persistence/validation, and startup materials authorization/type rules. */
@ExtendWith(MockitoExtension.class)
class StartupServiceTest {

    @Mock private StartupRepository startupRepository;
    @Mock private StartupTeamMemberRepository teamMemberRepository;
    @Mock private StartupUpdateRepository updateRepository;
    @Mock private StartupRoleRepository roleRepository;
    @Mock private StartupFollowRepository followRepository;
    @Mock private StartupMaterialRepository materialRepository;
    @Mock private StartupProfileViewRepository profileViewRepository;
    @Mock private UserRepository userRepository;
    @Mock private OpportunityRepository opportunityRepository;
    @Mock private IdeaRepository ideaRepository;
    @Mock private UserService userService;
    @Mock private NotificationService notificationService;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditService auditService;

    private final StartupMapper startupMapper = new StartupMapper();

    private StartupService service() {
        return new StartupService(startupRepository, teamMemberRepository, updateRepository, roleRepository,
                followRepository, materialRepository, profileViewRepository, userRepository, opportunityRepository, ideaRepository, userService, startupMapper,
                notificationService, fileStorageService, auditService,
                new StartupAccessPolicy(startupRepository, teamMemberRepository));
    }

    private Startup startup(String id, StartupVisibility visibility, boolean isRaising, boolean fundraisingVisible) {
        return Startup.builder().id(id).name("Rocket Labs").stage(StartupStage.MVP)
                .visibility(visibility).isRaising(isRaising).fundraisingVisible(fundraisingVisible)
                .needs(new java.util.HashSet<>())
                .moderationStatus(com.nukkad.common.moderation.ModerationStatus.APPROVED).build();
    }

    private StartupTeamMember founder(String startupId, String userId) {
        return StartupTeamMember.builder().id("m1").startupId(startupId).userId(userId)
                .teamRole(StartupTeamMember.TeamRole.FOUNDER).status(StartupTeamMember.Status.ACTIVE).build();
    }

    private StartupTeamMember admin(String startupId, String userId) {
        return StartupTeamMember.builder().id("m-" + userId).startupId(startupId).userId(userId)
                .teamRole(StartupTeamMember.TeamRole.ADMIN).status(StartupTeamMember.Status.ACTIVE).build();
    }

    private StartupTeamMember member(String startupId, String userId) {
        return StartupTeamMember.builder().id("m-" + userId).startupId(startupId).userId(userId)
                .teamRole(StartupTeamMember.TeamRole.MEMBER).status(StartupTeamMember.Status.ACTIVE).build();
    }

    /** Every UpdateStartupRequest field, defaulted to null/false, so each test only has to
     *  override the handful of fields it actually cares about — avoids miscounting the record's
     *  23 positional constructor args. */
    private static final class UpdateBuilder {
        String name, logoUrl, location, website, tagline, sector, problem, solution;
        String targetCustomer, businessModel, whatBuilding, stage, traction;
        String revenue, customers, users, growth, otherTraction, keywords, visibility;
        Boolean fundraisingVisible, isRaising;
        java.util.Set<String> needs;

        UpdateStartupRequest build() {
            return new UpdateStartupRequest(name, logoUrl, location, website, tagline, sector, problem, solution,
                    targetCustomer, businessModel, whatBuilding, stage, traction, revenue, customers, users, growth,
                    otherTraction, keywords, visibility, fundraisingVisible, isRaising, needs);
        }
    }

    // ---- visibility enforcement ----

    @Test
    void anonymousViewerIsBlockedFromAMemberOnlyStartup() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.NUKKAD_MEMBERS, false, true)));

        assertThatThrownBy(() -> service().getStartup("s1", null)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anonymousViewerCanSeeAPublicStartup() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));

        StartupDto dto = service().getStartup("s1", null);

        assertThat(dto.id()).isEqualTo("s1");
        assertThat(dto.visibility()).isEqualTo("Public");
    }

    @Test
    void anyAuthenticatedUserCanSeeAMemberOnlyStartup() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.NUKKAD_MEMBERS, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "randomUser")).thenReturn(Optional.empty());

        StartupDto dto = service().getStartup("s1", "randomUser");

        assertThat(dto.id()).isEqualTo("s1");
    }

    // ---- admin moderation ----

    @Test
    void publicGetterThrowsNotFoundForARemovedStartup() {
        Startup startup = startup("s1", StartupVisibility.PUBLIC, false, true);
        startup.setRemovedByAdmin(true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup));

        assertThatThrownBy(() -> service().getStartup("s1", null)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminGetterStillReturnsARemovedStartup() {
        Startup startup = startup("s1", StartupVisibility.PUBLIC, false, true);
        startup.setRemovedByAdmin(true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup));

        StartupDto dto = service().getStartupForAdmin("s1");

        assertThat(dto.removedByAdmin()).isTrue();
    }

    @Test
    void adminCanRemoveAndRestoreAStartupWithAudit() {
        Startup startup = startup("s1", StartupVisibility.PUBLIC, false, true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup));
        when(startupRepository.saveAndFlush(any(Startup.class))).thenAnswer(inv -> inv.getArgument(0));

        StartupDto removed = service().setRemovedByAdmin("admin1", "s1", true, "Fraudulent", "9.9.9.9");
        assertThat(removed.removedByAdmin()).isTrue();
        assertThat(removed.removalReason()).isEqualTo("Fraudulent");
        verify(auditService).log(eq("admin1"), eq(com.nukkad.common.audit.AuditAction.ADMIN_CONTENT_REMOVED),
                eq("Startup"), eq("s1"), eq("9.9.9.9"), any());

        StartupDto restored = service().setRemovedByAdmin("admin1", "s1", false, null, "9.9.9.9");
        assertThat(restored.removedByAdmin()).isFalse();
        verify(auditService).log(eq("admin1"), eq(com.nukkad.common.audit.AuditAction.ADMIN_CONTENT_RESTORED),
                eq("Startup"), eq("s1"), eq("9.9.9.9"), any());
    }

    // ---- moderation status ----
    // New startups are live at once (no review). REJECTED can still exist on startups an admin rejected earlier, and
    // stays visible only to the startup's own managers.

    private Startup rejectedStartup() {
        Startup startup = startup("s1", StartupVisibility.PUBLIC, false, true);
        startup.setModerationStatus(com.nukkad.common.moderation.ModerationStatus.REJECTED);
        return startup;
    }

    @Test
    void aNewStartupIsApprovedStraightAwayAndItsCreatorBecomesFounder() {
        when(startupRepository.saveAndFlush(any(Startup.class))).thenAnswer(inv -> {
            Startup saved = inv.getArgument(0);
            saved.setId("s-new");
            return saved;
        });

        StartupDto dto = service().createStartup("creator1",
                new com.nukkad.startup.dto.CreateStartupRequest("Rocket Labs", null, null, null, null, null, null, null, null));

        assertThat(dto.moderationStatus()).isEqualTo("APPROVED");
        org.mockito.ArgumentCaptor<Startup> saved = org.mockito.ArgumentCaptor.forClass(Startup.class);
        verify(startupRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getModerationStatus()).isEqualTo(com.nukkad.common.moderation.ModerationStatus.APPROVED);
        org.mockito.ArgumentCaptor<StartupTeamMember> member = org.mockito.ArgumentCaptor.forClass(StartupTeamMember.class);
        verify(teamMemberRepository).save(member.capture());
        assertThat(member.getValue().getUserId()).isEqualTo("creator1");
        assertThat(member.getValue().getTeamRole()).isEqualTo(StartupTeamMember.TeamRole.FOUNDER);
    }

    // ---- create-startup flow: the whole profile arrives in one call ----

    private com.nukkad.startup.dto.CreateStartupRequest completeRequest(String website, String visibility, Boolean fundraisingVisible) {
        return new com.nukkad.startup.dto.CreateStartupRequest("  Rocket Labs  ", null, "Rockets for everyone", "Aerospace", "Launches are costly",
                "Reusable boosters", "MVP", java.util.Set.of("Engineers", "Funding"), null,
                "Pune, India", website, "Small satellite makers", "Per-launch fee", "A reusable booster",
                "0", "12 paying", "", "8% MoM", "Two pilot launches booked", visibility, fundraisingVisible);
    }

    private Startup savedByCreate(com.nukkad.startup.dto.CreateStartupRequest request) {
        saveWithId();
        service().createStartup("creator1", request);
        org.mockito.ArgumentCaptor<Startup> saved = org.mockito.ArgumentCaptor.forClass(Startup.class);
        verify(startupRepository).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    @Test
    void aStartupIsCreatedCompleteWithProfileTractionAndVisibilityInOneCall() {
        Startup saved = savedByCreate(completeRequest("rocketlabs.example", "Nukkad Members", false));

        assertThat(saved.getName()).isEqualTo("Rocket Labs");
        assertThat(saved.getLocation()).isEqualTo("Pune, India");
        assertThat(saved.getWebsite()).isEqualTo("https://rocketlabs.example");
        assertThat(saved.getTargetCustomer()).isEqualTo("Small satellite makers");
        assertThat(saved.getBusinessModel()).isEqualTo("Per-launch fee");
        assertThat(saved.getWhatBuilding()).isEqualTo("A reusable booster");
        assertThat(saved.getStage()).isEqualTo(StartupStage.MVP);
        assertThat(saved.getVisibility()).isEqualTo(StartupVisibility.NUKKAD_MEMBERS);
        assertThat(saved.isFundraisingVisible()).isFalse();
        assertThat(saved.getNeeds()).containsExactlyInAnyOrder("Engineers", "Funding");
    }

    @Test
    void tractionFiguresAreKeptAsTypedZeroIsAValueAndBlankStaysBlank() {
        Startup saved = savedByCreate(completeRequest(null, null, null));

        assertThat(saved.getRevenue()).isEqualTo("0");
        assertThat(saved.getCustomers()).isEqualTo("12 paying");
        assertThat(saved.getUsers()).isNull();
        assertThat(saved.getGrowth()).isEqualTo("8% MoM");
        assertThat(saved.getOtherTraction()).isEqualTo("Two pilot launches booked");
    }

    @Test
    void aStartupCreatedWithoutTheNewFieldsIsPublicWithFundraisingVisibleAndNothingElseFilledIn() {
        Startup saved = savedByCreate(newStartupRequest());

        assertThat(saved.getVisibility()).isEqualTo(StartupVisibility.PUBLIC);
        assertThat(saved.isFundraisingVisible()).isTrue();
        assertThat(saved.getWebsite()).isNull();
        assertThat(saved.getLocation()).isNull();
        assertThat(saved.getRevenue()).isNull();
        assertThat(saved.getOtherTraction()).isNull();
        assertThat(saved.getStage()).isEqualTo(StartupStage.IDEA);
    }

    @Test
    void anInvalidWebsiteIsRefusedOnCreateAndNothingIsSaved() {
        assertThatThrownBy(() -> service().createStartup("creator1", completeRequest("not a url", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Website is not a valid URL");

        verify(startupRepository, never()).saveAndFlush(any(Startup.class));
        verify(teamMemberRepository, never()).save(any(StartupTeamMember.class));
    }

    @Test
    void anUnknownVisibilityOrStageIsRefusedOnCreateAndNothingIsSaved() {
        assertThatThrownBy(() -> service().createStartup("creator1", completeRequest(null, "Everyone", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unknown visibility: Everyone");
        assertThatThrownBy(() -> service().createStartup("creator1", new com.nukkad.startup.dto.CreateStartupRequest(
                "Rocket Labs", null, null, null, null, null, "Unicorn", null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(startupRepository, never()).saveAndFlush(any(Startup.class));
    }

    // ---- admin adds a startup ----

    private com.nukkad.startup.dto.CreateStartupRequest newStartupRequest() {
        return new com.nukkad.startup.dto.CreateStartupRequest("Rocket Labs", null, null, null, null, null, null, null, null);
    }

    private void saveWithId() {
        when(startupRepository.saveAndFlush(any(Startup.class))).thenAnswer(inv -> {
            Startup saved = inv.getArgument(0);
            saved.setId("s-new");
            return saved;
        });
    }

    @Test
    void anAdminWithNoFounderEmailOwnsTheStartupThemselves() {
        saveWithId();

        StartupDto dto = service().createStartupAsAdmin("admin1", newStartupRequest(), "  ", "1.2.3.4");

        assertThat(dto.moderationStatus()).isEqualTo("APPROVED");
        org.mockito.ArgumentCaptor<StartupTeamMember> member = org.mockito.ArgumentCaptor.forClass(StartupTeamMember.class);
        verify(teamMemberRepository).save(member.capture());
        assertThat(member.getValue().getUserId()).isEqualTo("admin1");
        assertThat(member.getValue().getTeamRole()).isEqualTo(StartupTeamMember.TeamRole.FOUNDER);
        verify(auditService).log(eq("admin1"), eq(com.nukkad.common.audit.AuditAction.ADMIN_STARTUP_CREATED),
                eq("Startup"), eq("s-new"), eq("1.2.3.4"), any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anAdminCanMakeAMemberTheFounderByEmailAndTheMemberIsTold() {
        saveWithId();
        when(userRepository.findByEmail("founder@example.com"))
                .thenReturn(Optional.of(User.builder().id("founder1").email("founder@example.com").build()));

        service().createStartupAsAdmin("admin1", newStartupRequest(), "  Founder@Example.com ", "1.2.3.4");

        org.mockito.ArgumentCaptor<StartupTeamMember> member = org.mockito.ArgumentCaptor.forClass(StartupTeamMember.class);
        verify(teamMemberRepository).save(member.capture());
        assertThat(member.getValue().getUserId()).isEqualTo("founder1");
        verify(auditService).log(eq("admin1"), eq(com.nukkad.common.audit.AuditAction.ADMIN_STARTUP_CREATED),
                eq("Startup"), eq("s-new"), eq("1.2.3.4"), any());
        verify(notificationService).notify(eq("founder1"), any(), anyString(), anyString(), eq("s-new"), eq("admin1"));
    }

    @Test
    void anUnknownFounderEmailIsRejectedAndNothingIsCreated() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().createStartupAsAdmin("admin1", newStartupRequest(), "nobody@example.com", "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);

        verify(startupRepository, never()).saveAndFlush(any(Startup.class));
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aSuspendedMemberCannotBeMadeFounder() {
        when(userRepository.findByEmail("sus@example.com")).thenReturn(Optional.of(
                User.builder().id("sus1").email("sus@example.com").status(com.nukkad.user.entity.AccountStatus.SUSPENDED).build()));

        assertThatThrownBy(() -> service().createStartupAsAdmin("admin1", newStartupRequest(), "sus@example.com", "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);

        verify(startupRepository, never()).saveAndFlush(any(Startup.class));
    }

    // ---- admin sets a startup's logo ----

    @Test
    void anAdminCanSetALogoEvenWhenNotAManagerOfThatStartup() {
        Startup existing = startup("s1", StartupVisibility.PUBLIC, false, true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(existing));
        when(startupRepository.save(any(Startup.class))).thenAnswer(inv -> inv.getArgument(0));
        org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(fileStorageService.storeImage(file, "startup-logos")).thenReturn("https://cdn.example.com/logo.png");
        // No team-membership lookup is stubbed for "admin1" at all — proving canManageStartup is never consulted.

        StartupDto dto = service().updateLogoAsAdmin("s1", file);

        assertThat(dto).isNotNull();
        org.mockito.ArgumentCaptor<Startup> saved = org.mockito.ArgumentCaptor.forClass(Startup.class);
        verify(startupRepository).save(saved.capture());
        assertThat(saved.getValue().getLogoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        verify(teamMemberRepository, never()).findByStartupIdAndUserId(any(), any());
    }

    @Test
    void settingALogoOnARemovedStartupAsAdminIsRefused() {
        removedStartupWithFounder();
        org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);

        assertThatThrownBy(() -> service().updateLogoAsAdmin("s1", file)).isInstanceOf(ResourceNotFoundException.class);

        verify(startupRepository, never()).save(any());
    }

    @Test
    void publicGetterHidesARejectedStartupFromANonFounder() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(rejectedStartup()));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "randomUser")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getStartup("s1", "randomUser")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void publicGetterStillShowsARejectedStartupToItsFounder() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(rejectedStartup()));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "founder1")).thenReturn(Optional.of(founder("s1", "founder1")));

        StartupDto dto = service().getStartup("s1", "founder1");

        assertThat(dto.moderationStatus()).isEqualTo("REJECTED");
    }

    // ---- sub-resources follow the startup's own visibility (one shared gate) ----

    private Startup removedStartup() {
        Startup startup = startup("s1", StartupVisibility.PUBLIC, false, true);
        startup.setRemovedByAdmin(true);
        return startup;
    }

    @Test
    void aRemovedStartupExposesNoSubResourceToAnyone() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(removedStartup()));

        for (String viewer : new String[] {"stranger1", "founder1"}) {
            assertThatThrownBy(() -> service().getMembers("s1", viewer)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service().getUpdates("s1", viewer)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service().getRoles("s1", viewer)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service().getMaterials("s1", viewer)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service().getMyMembership(viewer, "s1")).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    void aRejectedStartupExposesNoSubResourceToAStranger() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(rejectedStartup()));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "stranger1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMembers("s1", "stranger1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().getUpdates("s1", "stranger1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().getRoles("s1", "stranger1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().getMaterials("s1", "stranger1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aRejectedStartupStillShowsItsTeamToItsOwnFounder() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(rejectedStartup()));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "founder1")).thenReturn(Optional.of(founder("s1", "founder1")));
        when(teamMemberRepository.findByStartupIdAndStatus("s1", StartupTeamMember.Status.ACTIVE)).thenReturn(java.util.List.of(founder("s1", "founder1")));

        assertThat(service().getMembers("s1", "founder1")).hasSize(1);
    }

    @Test
    void aLiveStartupShowsItsTeamToAnySignedInMember() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndStatus("s1", StartupTeamMember.Status.ACTIVE)).thenReturn(java.util.List.of(founder("s1", "founder1")));

        assertThat(service().getMembers("s1", "anyMember")).hasSize(1);
    }

    @Test
    void nobodyCanFollowOrAskToJoinAStartupTheyCannotSee() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(removedStartup()));

        assertThatThrownBy(() -> service().toggleFollow("user1", "s1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().requestToJoin("user1", "s1", null, null)).isInstanceOf(ResourceNotFoundException.class);
        verify(followRepository, never()).save(any());
    }

    // ---- discovery: follower counts (hidden from everyone but the startup's own founders/admins) and the sectors filter ----

    @Test
    void aStartupPageShowsItsRealFollowerCountToAFounderOrAdminOfIt() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "founder1")).thenReturn(Optional.of(founder("s1", "founder1")));
        when(followRepository.countByStartupId("s1")).thenReturn(12L);

        assertThat(service().getStartup("s1", "founder1").followerCount()).isEqualTo(12L);
    }

    @Test
    void aStartupPageHidesItsFollowerCountFromAViewerWhoDoesNotManageIt() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(followRepository.countByStartupId("s1")).thenReturn(12L);

        // Following still works either way (see nobodyCanFollowOrAskToJoinAStartupTheyCannotSee et al.) — this
        // only hides the number, and a real anonymous/member viewer never manages someone else's startup.
        assertThat(service().getStartup("s1", "viewer1").followerCount()).isZero();
        assertThat(service().getStartup("s1", null).followerCount()).isZero();
    }

    @Test
    void aListOfStartupsGetsFollowerCountsFromOneQueryButOnlyRevealsThemForStartupsTheViewerManages() {
        Startup a = startup("s1", StartupVisibility.PUBLIC, false, true);
        Startup b = startup("s2", StartupVisibility.PUBLIC, false, true);
        when(startupRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(a, b)));
        when(followRepository.countByStartupIds(any())).thenReturn(java.util.List.of(new Object[] {"s1", 3L}, new Object[] {"s2", 7L}));
        // The viewer manages s1 (so its real count of 3 shows) but not s2 (whose real count of 7 stays hidden).
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "viewer1")).thenReturn(Optional.of(founder("s1", "viewer1")));

        var page = service().listStartups(null, null, null, null, null, null, "viewer1", 0, 20);

        assertThat(page.getContent()).extracting("id", "followerCount").containsExactly(
                org.assertj.core.groups.Tuple.tuple("s1", 3L), org.assertj.core.groups.Tuple.tuple("s2", 0L));
        verify(followRepository, org.mockito.Mockito.times(1)).countByStartupIds(any());
    }

    @Test
    void sectorsAreFoldedByCaseTrimmedAndOrderedByHowManyStartupsHaveThem() {
        when(startupRepository.countBySector(true)).thenReturn(java.util.List.of(
                new Object[] {"AI", 3L}, new Object[] {"ai", 1L}, new Object[] {"Fintech", 2L}, new Object[] {"  Health ", 5L}));

        var sectors = service().listSectors("viewer1");

        assertThat(sectors).extracting("sector", "count").containsExactly(
                org.assertj.core.groups.Tuple.tuple("Health", 5L),
                org.assertj.core.groups.Tuple.tuple("AI", 4L),
                org.assertj.core.groups.Tuple.tuple("Fintech", 2L));
    }

    @Test
    void anAnonymousCallerOnlyCountsPublicStartupsAndNoStartupsMeansNoSectors() {
        when(startupRepository.countBySector(false)).thenReturn(java.util.List.of());

        assertThat(service().listSectors(null)).isEmpty();

        verify(startupRepository).countBySector(false);
        verify(startupRepository, never()).countBySector(true);
    }

    // ---- fundraising status suppression ----

    @Test
    void isRaisingIsHiddenFromANonMemberWhenFundraisingVisibilityIsOff() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, true, false)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "viewer")).thenReturn(Optional.empty());

        StartupDto dto = service().getStartup("s1", "viewer");

        assertThat(dto.isRaising()).isFalse();
        assertThat(dto.fundraisingVisible()).isFalse();
    }

    @Test
    void isRaisingIsVisibleWhenFundraisingVisibilityIsOn() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, true, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "viewer")).thenReturn(Optional.empty());

        StartupDto dto = service().getStartup("s1", "viewer");

        assertThat(dto.isRaising()).isTrue();
    }

    @Test
    void teamMemberSeesIsRaisingEvenWhenFundraisingVisibilityIsOff() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, true, false)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "teammate"))
                .thenReturn(Optional.of(StartupTeamMember.builder().startupId("s1").userId("teammate")
                        .teamRole(StartupTeamMember.TeamRole.MEMBER).status(StartupTeamMember.Status.ACTIVE).build()));

        StartupDto dto = service().getStartup("s1", "teammate");

        assertThat(dto.isRaising()).isTrue();
    }

    // ---- the "Raising Now" filter must not reveal hidden fundraising ----

    /** The rule the public listing hands to the database for these arguments, read back as text (see CriteriaRecorder). */
    @SuppressWarnings("unchecked")
    private String listingRule(Boolean isRaising, String viewerId) {
        when(startupRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
        service().listStartups(null, null, null, isRaising, null, null, viewerId, 0, 12);
        org.mockito.ArgumentCaptor<org.springframework.data.jpa.domain.Specification<Startup>> spec =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.jpa.domain.Specification.class);
        verify(startupRepository).findAll(spec.capture(), any(org.springframework.data.domain.Pageable.class));
        return new com.nukkad.startup.repository.CriteriaRecorder().render(spec.getValue());
    }

    @Test
    void theRaisingNowFilterExcludesStartupsWithHiddenFundraisingUnlessTheViewerIsOnTheirTeam() {
        String rule = listingRule(true, "viewer1");

        assertThat(rule).contains("isTrue(isRaising)", "isTrue(fundraisingVisible)", "in(subquery(String))");
    }

    @Test
    void theRaisingNowFilterForAnAnonymousCallerNeverMatchesHiddenFundraising() {
        String rule = listingRule(true, null);

        assertThat(rule).contains("isTrue(isRaising)", "isTrue(fundraisingVisible)").doesNotContain("subquery");
    }

    @Test
    void listingWithoutTheRaisingFilterAddsNoFundraisingRuleAtAll() {
        assertThat(listingRule(null, "viewer1")).doesNotContain("isRaising").doesNotContain("fundraisingVisible");
    }

    // ---- rich profile persistence & validation ----

    @Test
    void updatingPersistsAllNewRichProfileFields() {
        Startup startup = startup("s1", StartupVisibility.PUBLIC, false, true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(startupRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBuilder u = new UpdateBuilder();
        u.location = "Bengaluru";
        u.website = "example.com";
        u.targetCustomer = "SMB retailers";
        u.businessModel = "B2B SaaS";
        u.whatBuilding = "An inventory copilot";
        u.revenue = "₹10L MRR";
        u.customers = "450";
        u.users = "12,500";
        u.growth = "20% MoM";
        u.otherTraction = "Featured in TechCrunch";
        u.keywords = "ai, retail,  inventory";

        StartupDto dto = service().updateStartup("f1", "s1", u.build());

        assertThat(dto.location()).isEqualTo("Bengaluru");
        assertThat(dto.website()).isEqualTo("https://example.com");
        assertThat(dto.targetCustomer()).isEqualTo("SMB retailers");
        assertThat(dto.businessModel()).isEqualTo("B2B SaaS");
        assertThat(dto.whatBuilding()).isEqualTo("An inventory copilot");
        assertThat(dto.revenue()).isEqualTo("₹10L MRR");
        assertThat(dto.customers()).isEqualTo("450");
        assertThat(dto.users()).isEqualTo("12,500");
        assertThat(dto.growth()).isEqualTo("20% MoM");
        assertThat(dto.otherTraction()).isEqualTo("Featured in TechCrunch");
        assertThat(dto.keywords()).isEqualTo("ai, retail, inventory");
    }

    @Test
    void updatingWithAMalformedWebsiteUrlIsRejected() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));

        UpdateBuilder u = new UpdateBuilder();
        u.website = "not a url with spaces";

        assertThatThrownBy(() -> service().updateStartup("f1", "s1", u.build())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void nonFounderCannotUpdateTheStartup() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "notFounder")).thenReturn(Optional.empty());

        UpdateBuilder u = new UpdateBuilder();
        u.tagline = "Hijacked";

        assertThatThrownBy(() -> service().updateStartup("notFounder", "s1", u.build())).isInstanceOf(ForbiddenException.class);
        verify(startupRepository, never()).saveAndFlush(any());
    }

    @Test
    void settingVisibilityToNukkadMembersPersists() {
        Startup startup = startup("s1", StartupVisibility.PUBLIC, false, true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(startupRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBuilder u = new UpdateBuilder();
        u.visibility = "Nukkad Members";

        StartupDto dto = service().updateStartup("f1", "s1", u.build());

        assertThat(dto.visibility()).isEqualTo("Nukkad Members");
    }

    @Test
    void turningFundraisingVisibilityOffPersists() {
        Startup startup = startup("s1", StartupVisibility.PUBLIC, true, true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(startupRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBuilder u = new UpdateBuilder();
        u.fundraisingVisible = false;

        StartupDto dto = service().updateStartup("f1", "s1", u.build());

        assertThat(dto.fundraisingVisible()).isFalse();
    }

    // ---- materials ----

    @Test
    void founderCanAddAnExternalLinkMaterial() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(materialRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        StartupMaterialDto dto = service().addMaterial("f1", "s1", "Website", null, "example.com", null);

        assertThat(dto.materialType()).isEqualTo("Website");
        assertThat(dto.url()).isEqualTo("https://example.com");
    }

    @Test
    void nonFounderCannotAddMaterial() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "notFounder")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().addMaterial("notFounder", "s1", "Website", null, "example.com", null))
                .isInstanceOf(ForbiddenException.class);
        verify(materialRepository, never()).saveAndFlush(any());
    }

    @Test
    void externalLinkMaterialRejectsAFile() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", "x".getBytes());

        assertThatThrownBy(() -> service().addMaterial("f1", "s1", "Website", null, null, file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadMaterialRejectsAUrl() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));

        assertThatThrownBy(() -> service().addMaterial("f1", "s1", "Screenshots", null, "example.com", null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void screenshotsRejectsAPdfUpload() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", "x".getBytes());
        when(fileStorageService.storeMedia(any(), eq("startup-materials")))
                .thenReturn(new FileStorageService.StoredMedia("https://storage.example.com/x.pdf", FileStorageService.AttachmentKind.PDF));

        assertThatThrownBy(() -> service().addMaterial("f1", "s1", "Screenshots", null, null, file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void pitchDeckUploadStoresThroughFileStorageServiceAndPersistsTheHostedUrl() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", "x".getBytes());
        when(fileStorageService.storeMedia(any(), eq("startup-materials")))
                .thenReturn(new FileStorageService.StoredMedia("https://storage.example.com/startup-materials/abc.pdf", FileStorageService.AttachmentKind.PDF));
        when(materialRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        StartupMaterialDto dto = service().addMaterial("f1", "s1", "Pitch Deck", "Seed deck", null, file);

        assertThat(dto.url()).isEqualTo("https://storage.example.com/startup-materials/abc.pdf");
        assertThat(dto.originalFileName()).isEqualTo("deck.pdf");
    }

    @Test
    void nonFounderCannotDeleteMaterial() {
        StartupMaterial material = StartupMaterial.builder().id("mat1").startupId("s1")
                .materialType(StartupMaterialType.WEBSITE).url("https://example.com").createdByUserId("f1").build();
        when(materialRepository.findById("mat1")).thenReturn(Optional.of(material));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "notFounder")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deleteMaterial("notFounder", "mat1")).isInstanceOf(ForbiddenException.class);
        verify(materialRepository, never()).delete(any(StartupMaterial.class));
    }

    // ---- profile completion percentage is real, not fabricated ----

    @Test
    void profileCompletionPercentReflectsWhatIsActuallyFilledIn() {
        Startup bare = Startup.builder().id("s1").name("Bare Startup").stage(StartupStage.IDEA)
                .visibility(StartupVisibility.PUBLIC).needs(new java.util.HashSet<>()).build();
        StartupDto bareDto = startupMapper.toDto(bare, false, false, true, 0);

        Startup fuller = Startup.builder().id("s2").name("Fuller Startup").stage(StartupStage.IDEA)
                .visibility(StartupVisibility.PUBLIC).logoUrl("logo.png").location("Delhi").website("https://x.com")
                .tagline("t").sector("Fintech").problem("p").solution("sol").targetCustomer("tc")
                .businessModel("bm").whatBuilding("wb").revenue("₹1L").needs(java.util.Set.of("Funding")).build();
        StartupDto fullerDto = startupMapper.toDto(fuller, false, false, true, 0);

        assertThat(bareDto.profileCompletionPercent()).isZero();
        assertThat(fullerDto.profileCompletionPercent()).isGreaterThan(bareDto.profileCompletionPercent());
        assertThat(fullerDto.profileCompletionPercent()).isEqualTo(100);
    }

    // ---- 3-tier team roles: Founder / Admin / Member ----

    @Test
    void adminCanUpdateTheStartup() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a1")).thenReturn(Optional.of(admin("s1", "a1")));
        when(startupRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBuilder u = new UpdateBuilder();
        u.tagline = "Updated by admin";

        StartupDto dto = service().updateStartup("a1", "s1", u.build());

        assertThat(dto.tagline()).isEqualTo("Updated by admin");
    }

    @Test
    void plainMemberCannotUpdateTheStartup() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "m1")).thenReturn(Optional.of(member("s1", "m1")));

        UpdateBuilder u = new UpdateBuilder();
        u.tagline = "Hijacked";

        assertThatThrownBy(() -> service().updateStartup("m1", "s1", u.build())).isInstanceOf(ForbiddenException.class);
        verify(startupRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminCannotDeleteTheStartup() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a1")).thenReturn(Optional.of(admin("s1", "a1")));

        assertThatThrownBy(() -> service().deleteStartup("a1", "s1")).isInstanceOf(ForbiddenException.class);
        verify(startupRepository, never()).deleteById(any());
    }

    @Test
    void founderCanDeleteTheStartup() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));

        service().deleteStartup("f1", "s1");

        verify(startupRepository).deleteById("s1");
    }

    @Test
    void aStartupWithOpportunitiesPostedForItCannotBeDeletedAndTheMessageSaysWhy() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(opportunityRepository.countByStartupId("s1")).thenReturn(2L);

        assertThatThrownBy(() -> service().deleteStartup("f1", "s1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Rocket Labs")
                .hasMessageContaining("2 opportunities")
                .hasMessageContaining("posted");

        verify(startupRepository, never()).deleteById(any());
        verify(ideaRepository, never()).detachFromStartup(any());
    }

    @Test
    void theRefusalToDeleteNamesASingleOpportunityInTheSingular() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(opportunityRepository.countByStartupId("s1")).thenReturn(1L);

        assertThatThrownBy(() -> service().deleteStartup("f1", "s1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("1 opportunity ");
    }

    @Test
    void deletingAStartupThatCameFromAnIdeaReleasesTheIdeaFirst() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(opportunityRepository.countByStartupId("s1")).thenReturn(0L);

        service().deleteStartup("f1", "s1");

        var order = inOrder(ideaRepository, startupRepository);
        order.verify(ideaRepository).detachFromStartup("s1");
        order.verify(startupRepository).deleteById("s1");
    }

    @Test
    void anAdminIsRefusedTheDeleteBeforeAnythingIsCheckedOrTouched() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a1")).thenReturn(Optional.of(admin("s1", "a1")));

        assertThatThrownBy(() -> service().deleteStartup("a1", "s1")).isInstanceOf(ForbiddenException.class);

        verify(opportunityRepository, never()).countByStartupId(any());
        verify(ideaRepository, never()).detachFromStartup(any());
        verify(startupRepository, never()).deleteById(any());
    }

    @Test
    void anAdminMembershipThatIsNotActiveCannotEditTheStartup() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        StartupTeamMember pendingAdmin = admin("s1", "a1");
        pendingAdmin.setStatus(StartupTeamMember.Status.PENDING);
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a1")).thenReturn(Optional.of(pendingAdmin));

        UpdateBuilder u = new UpdateBuilder();
        u.tagline = "Nope";

        assertThatThrownBy(() -> service().updateStartup("a1", "s1", u.build())).isInstanceOf(ForbiddenException.class);
        verify(startupRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminCanAddATeammateAsAMember() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a1")).thenReturn(Optional.of(admin("s1", "a1")));
        when(userRepository.findById("newUser")).thenReturn(Optional.of(User.builder().id("newUser").build()));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "newUser")).thenReturn(Optional.empty());
        when(teamMemberRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        StartupTeamMemberDto dto = service().addMember("a1", "s1", "newUser", null, null);

        assertThat(dto.teamRole()).isEqualTo("MEMBER");
    }

    @Test
    void adminCannotGrantAdminAtAddTime() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a1")).thenReturn(Optional.of(admin("s1", "a1")));
        when(userRepository.findById("newUser")).thenReturn(Optional.of(User.builder().id("newUser").build()));

        assertThatThrownBy(() -> service().addMember("a1", "s1", "newUser", null, "ADMIN"))
                .isInstanceOf(ForbiddenException.class);
        verify(teamMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    void founderCanGrantAdminAtAddTime() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(userRepository.findById("newUser")).thenReturn(Optional.of(User.builder().id("newUser").build()));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "newUser")).thenReturn(Optional.empty());
        when(teamMemberRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        StartupTeamMemberDto dto = service().addMember("f1", "s1", "newUser", null, "ADMIN");

        assertThat(dto.teamRole()).isEqualTo("ADMIN");
    }

    @Test
    void plainMemberCannotAddATeammate() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "m1")).thenReturn(Optional.of(member("s1", "m1")));

        assertThatThrownBy(() -> service().addMember("m1", "s1", "newUser", null, null))
                .isInstanceOf(ForbiddenException.class);
        verify(teamMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminCannotRemoveAnotherAdmin() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a1")).thenReturn(Optional.of(admin("s1", "a1")));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a2")).thenReturn(Optional.of(admin("s1", "a2")));

        assertThatThrownBy(() -> service().removeMember("a1", "s1", "a2")).isInstanceOf(ForbiddenException.class);
        verify(teamMemberRepository, never()).delete(any(StartupTeamMember.class));
    }

    @Test
    void adminCanRemoveAPlainMember() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a1")).thenReturn(Optional.of(admin("s1", "a1")));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "m1")).thenReturn(Optional.of(member("s1", "m1")));

        service().removeMember("a1", "s1", "m1");

        verify(teamMemberRepository).delete(any(StartupTeamMember.class));
    }

    @Test
    void founderCanRemoveAnAdmin() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a1")).thenReturn(Optional.of(admin("s1", "a1")));

        service().removeMember("f1", "s1", "a1");

        verify(teamMemberRepository).delete(any(StartupTeamMember.class));
    }

    @Test
    void founderCanPromoteAMemberToAdmin() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "m1")).thenReturn(Optional.of(member("s1", "m1")));
        when(teamMemberRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        StartupTeamMemberDto dto = service().updateMemberRole("f1", "s1", "m1", "ADMIN");

        assertThat(dto.teamRole()).isEqualTo("ADMIN");
    }

    @Test
    void founderCanDemoteAnAdminBackToMember() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a1")).thenReturn(Optional.of(admin("s1", "a1")));
        when(teamMemberRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        StartupTeamMemberDto dto = service().updateMemberRole("f1", "s1", "a1", "MEMBER");

        assertThat(dto.teamRole()).isEqualTo("MEMBER");
    }

    @Test
    void nonFounderCannotChangeAnyonesRole() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "a1")).thenReturn(Optional.of(admin("s1", "a1")));

        assertThatThrownBy(() -> service().updateMemberRole("a1", "s1", "m1", "ADMIN"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theFoundersOwnRoleCanNeverBeChanged() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));

        assertThatThrownBy(() -> service().updateMemberRole("f1", "s1", "f1", "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void founderRoleCanNeverBeAssignedThroughTheApi() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));

        assertThatThrownBy(() -> service().updateMemberRole("f1", "s1", "m1", "FOUNDER"))
                .isInstanceOf(BadRequestException.class);
    }

    // ---- an admin-removed startup is frozen for its own team ----

    private void removedStartupWithFounder() {
        Startup removed = startup("s1", StartupVisibility.PUBLIC, false, true);
        removed.setRemovedByAdmin(true);
        // No team lookup is stubbed: a removed startup is refused before anyone's role is even consulted.
        when(startupRepository.findById("s1")).thenReturn(Optional.of(removed));
    }

    @Test
    void theFounderOfARemovedStartupCannotEditItOrChangeItsLogo() {
        removedStartupWithFounder();
        UpdateBuilder u = new UpdateBuilder();
        u.tagline = "Sneaky";

        assertThatThrownBy(() -> service().updateStartup("f1", "s1", u.build())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().removeLogo("f1", "s1")).isInstanceOf(ResourceNotFoundException.class);
        verify(startupRepository, never()).saveAndFlush(any());
        verify(startupRepository, never()).save(any());
    }

    @Test
    void theFounderOfARemovedStartupCannotDeleteItOrManageItsTeam() {
        removedStartupWithFounder();

        assertThatThrownBy(() -> service().deleteStartup("f1", "s1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().addMember("f1", "s1", "someone", null, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().removeMember("f1", "s1", "someone")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().updateMemberRole("f1", "s1", "someone", "ADMIN")).isInstanceOf(ResourceNotFoundException.class);
        verify(startupRepository, never()).deleteById(any());
        verify(teamMemberRepository, never()).saveAndFlush(any());
        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void theTeamOfARemovedStartupCannotPostUpdatesRolesOrMaterials() {
        removedStartupWithFounder();

        assertThatThrownBy(() -> service().postUpdate("m1", "s1", "hello")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().createRole("f1", "s1", new com.nukkad.startup.dto.CreateStartupRoleRequest("Dev", "Job", null, true)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().addMaterial("f1", "s1", "Website", null, "https://a.com", null)).isInstanceOf(ResourceNotFoundException.class);
        verify(updateRepository, never()).saveAndFlush(any());
        verify(roleRepository, never()).saveAndFlush(any());
        verify(materialRepository, never()).saveAndFlush(any());
    }

    @Test
    void aLiveStartupIsStillEditableByItsFounder() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "f1")).thenReturn(Optional.of(founder("s1", "f1")));
        when(startupRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        UpdateBuilder u = new UpdateBuilder();
        u.tagline = "Fine";

        assertThat(service().updateStartup("f1", "s1", u.build()).tagline()).isEqualTo("Fine");
    }

    // ---- the team list carries each person, so the profile needs no request per member ----

    @Test
    void theTeamListIncludesEachMembersProfileAsTheViewerMaySeeIt() {
        com.nukkad.user.dto.UserDto alice = org.mockito.Mockito.mock(com.nukkad.user.dto.UserDto.class);
        accessibleStartup();
        when(teamMemberRepository.findByStartupIdAndStatus("s1", StartupTeamMember.Status.ACTIVE))
                .thenReturn(java.util.List.of(founder("s1", "f1"), member("s1", "m1")));
        when(userService.getUser("f1", "viewer")).thenReturn(alice);
        when(userService.getUser("m1", "viewer")).thenThrow(new ResourceNotFoundException("blocked"));

        var members = service().getMembers("s1", "viewer");

        assertThat(members).hasSize(2);
        assertThat(members.get(0).user()).isSameAs(alice);
        assertThat(members.get(1).user()).isNull();
    }

    private void accessibleStartup() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1", StartupVisibility.PUBLIC, false, true)));
    }
}
