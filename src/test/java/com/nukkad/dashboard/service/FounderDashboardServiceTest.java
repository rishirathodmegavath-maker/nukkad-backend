package com.nukkad.dashboard.service;

import com.nukkad.dashboard.dto.FounderDashboardDto;
import com.nukkad.event.repository.EventAttendeeRepository;
import com.nukkad.investor.entity.IntroDirection;
import com.nukkad.investor.entity.IntroRequestStatus;
import com.nukkad.investor.repository.IntroRequestRepository;
import com.nukkad.opportunity.repository.OpportunityApplicantRepository;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.mapper.StartupMapper;
import com.nukkad.startup.repository.StartupFollowRepository;
import com.nukkad.startup.repository.StartupProfileViewRepository;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FounderDashboardServiceTest {

    private static final List<StartupTeamMember.TeamRole> MANAGER_ROLES =
            List.of(StartupTeamMember.TeamRole.FOUNDER, StartupTeamMember.TeamRole.ADMIN);

    @Mock private StartupTeamMemberRepository teamMemberRepository;
    @Mock private StartupRepository startupRepository;
    @Mock private StartupFollowRepository followRepository;
    @Mock private StartupProfileViewRepository profileViewRepository;
    @Mock private IntroRequestRepository introRequestRepository;
    @Mock private OpportunityApplicantRepository applicantRepository;
    @Mock private EventAttendeeRepository eventAttendeeRepository;

    private final StartupMapper startupMapper = new StartupMapper();

    private FounderDashboardService service() {
        return new FounderDashboardService(teamMemberRepository, startupRepository, followRepository,
                profileViewRepository, introRequestRepository, applicantRepository, eventAttendeeRepository, startupMapper);
    }

    private StartupTeamMember membership(String startupId, StartupTeamMember.TeamRole role) {
        return StartupTeamMember.builder().startupId(startupId).userId("u1").teamRole(role)
                .status(StartupTeamMember.Status.ACTIVE).build();
    }

    private Startup startup(String id, String name, long createdAtEpochSecond) {
        Startup startup = Startup.builder().id(id).name(name).stage(StartupStage.MVP).needs(new HashSet<>()).build();
        startup.setCreatedAt(Instant.ofEpochSecond(createdAtEpochSecond));
        return startup;
    }

    private void manages(StartupTeamMember... memberships) {
        when(teamMemberRepository.findByUserIdAndTeamRoleInAndStatus("u1", MANAGER_ROLES, StartupTeamMember.Status.ACTIVE))
                .thenReturn(List.of(memberships));
    }

    private void metrics(String startupId, long views, long followers, long interests, long applications, long rsvps) {
        when(profileViewRepository.countByStartupId(startupId)).thenReturn(views);
        when(followRepository.countByStartupId(startupId)).thenReturn(followers);
        when(introRequestRepository.countByStartupIdAndDirectionAndStatusNot(
                startupId, IntroDirection.INVESTOR_TO_FOUNDER, IntroRequestStatus.WITHDRAWN)).thenReturn(interests);
        when(applicantRepository.countByStartupId(startupId)).thenReturn(applications);
        when(eventAttendeeRepository.countRsvpsForStartupEvents(startupId, StartupTeamMember.Status.ACTIVE)).thenReturn(rsvps);
    }

    @Test
    void userWhoRunsNoStartupGetsAllZerosAndAnEmptyList() {
        manages();

        FounderDashboardDto dto = service().getDashboard("u1");

        assertThat(dto.hasFoundedStartup()).isFalse();
        assertThat(dto.startups()).isEmpty();
        assertThat(dto.profileViews()).isZero();
        assertThat(dto.investorInterests()).isZero();
        assertThat(dto.jobApplications()).isZero();
        assertThat(dto.followers()).isZero();
        assertThat(dto.eventRsvps()).isZero();
        assertThat(dto.profileCompletionPercent()).isZero();
    }

    @Test
    void reportsRealCountsForTheStartupAndTheTotals() {
        manages(membership("s1", StartupTeamMember.TeamRole.FOUNDER));
        when(startupRepository.findAllById(any())).thenReturn(List.of(startup("s1", "Ledgerly", 100)));
        metrics("s1", 42, 7, 3, 9, 5);

        FounderDashboardDto dto = service().getDashboard("u1");

        assertThat(dto.hasFoundedStartup()).isTrue();
        assertThat(dto.primaryStartupId()).isEqualTo("s1");
        assertThat(dto.primaryStartupName()).isEqualTo("Ledgerly");
        assertThat(dto.startupCount()).isEqualTo(1);
        assertThat(dto.profileViews()).isEqualTo(42);
        assertThat(dto.followers()).isEqualTo(7);
        assertThat(dto.investorInterests()).isEqualTo(3);
        assertThat(dto.jobApplications()).isEqualTo(9);
        assertThat(dto.eventRsvps()).isEqualTo(5);
        assertThat(dto.startups()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("s1");
            assertThat(item.teamRole()).isEqualTo("FOUNDER");
            assertThat(item.eventRsvps()).isEqualTo(5);
        });
    }

    @Test
    void eventRsvpsAreThoseToTheStartupsEventsNotTheFoundersOwnRsvps() {
        manages(membership("s1", StartupTeamMember.TeamRole.FOUNDER));
        when(startupRepository.findAllById(any())).thenReturn(List.of(startup("s1", "Ledgerly", 100)));
        metrics("s1", 0, 0, 0, 0, 11);

        FounderDashboardDto dto = service().getDashboard("u1");

        assertThat(dto.eventRsvps()).isEqualTo(11);
        verify(eventAttendeeRepository, never()).countByUserId(any());
    }

    @Test
    void aStartupAdminSeesTheStartupOnTheirDashboardToo() {
        manages(membership("s1", StartupTeamMember.TeamRole.ADMIN));
        when(startupRepository.findAllById(any())).thenReturn(List.of(startup("s1", "Ledgerly", 100)));
        metrics("s1", 4, 2, 1, 0, 0);

        FounderDashboardDto dto = service().getDashboard("u1");

        assertThat(dto.hasFoundedStartup()).isTrue();
        assertThat(dto.startups()).singleElement().satisfies(item -> assertThat(item.teamRole()).isEqualTo("ADMIN"));
        assertThat(dto.profileViews()).isEqualTo(4);
    }

    @Test
    void everyStartupIsReportedNotJustTheFirstAndTotalsAddUp() {
        manages(membership("s-admin", StartupTeamMember.TeamRole.ADMIN),
                membership("s-new", StartupTeamMember.TeamRole.FOUNDER),
                membership("s-old", StartupTeamMember.TeamRole.FOUNDER));
        when(startupRepository.findAllById(any())).thenReturn(List.of(
                startup("s-admin", "Admin Co", 10), startup("s-new", "Newer", 300), startup("s-old", "Older", 200)));
        metrics("s-admin", 1, 1, 1, 1, 1);
        metrics("s-new", 10, 2, 3, 4, 5);
        metrics("s-old", 100, 20, 30, 40, 50);

        FounderDashboardDto dto = service().getDashboard("u1");

        assertThat(dto.startupCount()).isEqualTo(3);
        assertThat(dto.startups()).extracting("id").containsExactly("s-old", "s-new", "s-admin");
        assertThat(dto.primaryStartupId()).isEqualTo("s-old");
        assertThat(dto.profileViews()).isEqualTo(111);
        assertThat(dto.followers()).isEqualTo(23);
        assertThat(dto.investorInterests()).isEqualTo(34);
        assertThat(dto.jobApplications()).isEqualTo(45);
        assertThat(dto.eventRsvps()).isEqualTo(56);
    }

    @Test
    void aStartupAnAdminRemovedIsLeftOffTheDashboard() {
        manages(membership("s-live", StartupTeamMember.TeamRole.FOUNDER), membership("s-gone", StartupTeamMember.TeamRole.FOUNDER));
        Startup gone = startup("s-gone", "Gone", 50);
        gone.setRemovedByAdmin(true);
        when(startupRepository.findAllById(any())).thenReturn(List.of(startup("s-live", "Live", 100), gone));
        metrics("s-live", 3, 0, 0, 0, 0);

        FounderDashboardDto dto = service().getDashboard("u1");

        assertThat(dto.startupCount()).isEqualTo(1);
        assertThat(dto.startups()).extracting("id").containsExactly("s-live");
        verify(profileViewRepository, never()).countByStartupId("s-gone");
    }

    @Test
    void ifEveryStartupWasRemovedTheDashboardIsTheEmptyOne() {
        manages(membership("s-gone", StartupTeamMember.TeamRole.FOUNDER));
        Startup gone = startup("s-gone", "Gone", 50);
        gone.setRemovedByAdmin(true);
        when(startupRepository.findAllById(any())).thenReturn(List.of(gone));

        FounderDashboardDto dto = service().getDashboard("u1");

        assertThat(dto.hasFoundedStartup()).isFalse();
        assertThat(dto.startups()).isEmpty();
    }
}
