package com.nukkad.dashboard.service;

import com.nukkad.dashboard.dto.FounderDashboardDto;
import com.nukkad.event.repository.EventAttendeeRepository;
import com.nukkad.investor.entity.IntroDirection;
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

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FounderDashboardServiceTest {

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

    private StartupTeamMember founderOf(String startupId) {
        return StartupTeamMember.builder().startupId(startupId).userId("founder1")
                .teamRole(StartupTeamMember.TeamRole.FOUNDER).status(StartupTeamMember.Status.ACTIVE).build();
    }

    @Test
    void userWithNoFoundedStartupsGetsAllZerosNotBlanks() {
        when(teamMemberRepository.findByUserIdAndTeamRoleInAndStatus(
                "user1", List.of(StartupTeamMember.TeamRole.FOUNDER), StartupTeamMember.Status.ACTIVE))
                .thenReturn(List.of());

        FounderDashboardDto dto = service().getDashboard("user1");

        assertThat(dto.hasFoundedStartup()).isFalse();
        assertThat(dto.profileViews()).isZero();
        assertThat(dto.investorInterests()).isZero();
        assertThat(dto.jobApplications()).isZero();
        assertThat(dto.followers()).isZero();
        assertThat(dto.eventRsvps()).isZero();
        assertThat(dto.profileCompletionPercent()).isZero();
    }

    @Test
    void aggregatesRealCountsAcrossASingleFoundedStartup() {
        when(teamMemberRepository.findByUserIdAndTeamRoleInAndStatus(
                "founder1", List.of(StartupTeamMember.TeamRole.FOUNDER), StartupTeamMember.Status.ACTIVE))
                .thenReturn(List.of(founderOf("s1")));

        Startup startup = Startup.builder().id("s1").name("Ledgerly").stage(StartupStage.MVP)
                .needs(new HashSet<>()).build();
        when(startupRepository.findAllById(List.of("s1"))).thenReturn(List.of(startup));
        when(profileViewRepository.countByStartupId("s1")).thenReturn(42L);
        when(followRepository.countByStartupId("s1")).thenReturn(7L);
        when(introRequestRepository.countByRecipientIdAndDirection("founder1", IntroDirection.INVESTOR_TO_FOUNDER)).thenReturn(3L);
        when(applicantRepository.countByPostedByUserId("founder1")).thenReturn(9L);
        when(eventAttendeeRepository.countByUserId("founder1")).thenReturn(2L);

        FounderDashboardDto dto = service().getDashboard("founder1");

        assertThat(dto.hasFoundedStartup()).isTrue();
        assertThat(dto.primaryStartupId()).isEqualTo("s1");
        assertThat(dto.primaryStartupName()).isEqualTo("Ledgerly");
        assertThat(dto.startupCount()).isEqualTo(1);
        assertThat(dto.profileViews()).isEqualTo(42);
        assertThat(dto.followers()).isEqualTo(7);
        assertThat(dto.investorInterests()).isEqualTo(3);
        assertThat(dto.jobApplications()).isEqualTo(9);
        assertThat(dto.eventRsvps()).isEqualTo(2);
    }

    @Test
    void sumsCountsAcrossMultipleFoundedStartups() {
        when(teamMemberRepository.findByUserIdAndTeamRoleInAndStatus(
                "founder1", List.of(StartupTeamMember.TeamRole.FOUNDER), StartupTeamMember.Status.ACTIVE))
                .thenReturn(List.of(founderOf("s1"), founderOf("s2")));

        Startup s1 = Startup.builder().id("s1").name("First").stage(StartupStage.MVP).needs(new HashSet<>()).build();
        Startup s2 = Startup.builder().id("s2").name("Second").stage(StartupStage.MVP).needs(new HashSet<>()).build();
        when(startupRepository.findAllById(List.of("s1", "s2"))).thenReturn(List.of(s1, s2));
        when(profileViewRepository.countByStartupId("s1")).thenReturn(10L);
        when(profileViewRepository.countByStartupId("s2")).thenReturn(5L);
        when(followRepository.countByStartupId("s1")).thenReturn(1L);
        when(followRepository.countByStartupId("s2")).thenReturn(2L);

        FounderDashboardDto dto = service().getDashboard("founder1");

        assertThat(dto.startupCount()).isEqualTo(2);
        assertThat(dto.profileViews()).isEqualTo(15);
        assertThat(dto.followers()).isEqualTo(3);
        assertThat(dto.primaryStartupId()).isEqualTo("s1");
    }
}
