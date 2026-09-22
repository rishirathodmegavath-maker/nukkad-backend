package com.nukkad.startup.service;

import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.entity.StartupVisibility;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** The one rule for "may this viewer read this startup, and everything hanging off it". */
@ExtendWith(MockitoExtension.class)
class StartupAccessPolicyTest {

    @Mock private StartupRepository startupRepository;
    @Mock private StartupTeamMemberRepository teamMemberRepository;

    private StartupAccessPolicy policy() {
        return new StartupAccessPolicy(startupRepository, teamMemberRepository);
    }

    private Startup startup(StartupVisibility visibility, ModerationStatus status, boolean removed) {
        Startup startup = Startup.builder().id("s1").name("Ledgerly").visibility(visibility).moderationStatus(status).build();
        startup.setRemovedByAdmin(removed);
        return startup;
    }

    private void teamRole(String userId, StartupTeamMember.TeamRole role, StartupTeamMember.Status status) {
        when(teamMemberRepository.findByStartupIdAndUserId("s1", userId)).thenReturn(Optional.of(
                StartupTeamMember.builder().startupId("s1").userId(userId).teamRole(role).status(status).build()));
    }

    @Test
    void aLiveStartupIsReadableByAnySignedInViewerAndAnAnonymousOneWhenPublic() {
        Startup live = startup(StartupVisibility.PUBLIC, ModerationStatus.APPROVED, false);

        assertThat(policy().isReadableBy(live, "anyone")).isTrue();
        assertThat(policy().isReadableBy(live, null)).isTrue();
    }

    @Test
    void aMemberOnlyStartupIsNotReadableByAnAnonymousCaller() {
        Startup membersOnly = startup(StartupVisibility.NUKKAD_MEMBERS, ModerationStatus.APPROVED, false);

        assertThat(policy().isReadableBy(membersOnly, null)).isFalse();
        assertThat(policy().isReadableBy(membersOnly, "signedInMember")).isTrue();
    }

    @Test
    void aRemovedStartupIsNotReadableByAnyone() {
        Startup removed = startup(StartupVisibility.PUBLIC, ModerationStatus.APPROVED, true);

        assertThat(policy().isReadableBy(removed, "stranger")).isFalse();
        assertThat(policy().isReadableBy(removed, "founder")).isFalse();
        assertThat(policy().isReadableBy(removed, null)).isFalse();
    }

    @Test
    void aRejectedStartupIsReadableOnlyByItsFoundersAndAdmins() {
        Startup rejected = startup(StartupVisibility.PUBLIC, ModerationStatus.REJECTED, false);
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "stranger")).thenReturn(Optional.empty());
        teamRole("founder", StartupTeamMember.TeamRole.FOUNDER, StartupTeamMember.Status.ACTIVE);
        teamRole("admin", StartupTeamMember.TeamRole.ADMIN, StartupTeamMember.Status.ACTIVE);
        teamRole("member", StartupTeamMember.TeamRole.MEMBER, StartupTeamMember.Status.ACTIVE);
        teamRole("pendingFounder", StartupTeamMember.TeamRole.FOUNDER, StartupTeamMember.Status.PENDING);

        assertThat(policy().isReadableBy(rejected, "stranger")).isFalse();
        assertThat(policy().isReadableBy(rejected, "member")).isFalse();
        assertThat(policy().isReadableBy(rejected, "pendingFounder")).isFalse();
        assertThat(policy().isReadableBy(rejected, null)).isFalse();
        assertThat(policy().isReadableBy(rejected, "founder")).isTrue();
        assertThat(policy().isReadableBy(rejected, "admin")).isTrue();
    }

    @Test
    void requireReadableAnswersTheSame404ForMissingAndForHiddenStartups() {
        when(startupRepository.findById("missing")).thenReturn(Optional.empty());
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup(StartupVisibility.PUBLIC, ModerationStatus.APPROVED, true)));

        assertThatThrownBy(() -> policy().requireReadable("missing", "u1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> policy().requireReadable("s1", "u1"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Startup not found: s1");
    }

    @Test
    void requireReadableReturnsTheStartupWhenAllowed() {
        Startup live = startup(StartupVisibility.PUBLIC, ModerationStatus.APPROVED, false);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(live));

        assertThat(policy().requireReadable("s1", "u1")).isSameAs(live);
    }

    @Test
    void canManageIsTrueOnlyForAnActiveFounderOrAdmin() {
        teamRole("founder", StartupTeamMember.TeamRole.FOUNDER, StartupTeamMember.Status.ACTIVE);
        teamRole("member", StartupTeamMember.TeamRole.MEMBER, StartupTeamMember.Status.ACTIVE);
        teamRole("rejectedAdmin", StartupTeamMember.TeamRole.ADMIN, StartupTeamMember.Status.REJECTED);

        assertThat(policy().canManage("s1", "founder")).isTrue();
        assertThat(policy().canManage("s1", "member")).isFalse();
        assertThat(policy().canManage("s1", "rejectedAdmin")).isFalse();
        assertThat(policy().canManage("s1", null)).isFalse();
    }
}
