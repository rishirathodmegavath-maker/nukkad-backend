package com.nukkad.investor.service;

import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.investor.dto.CreateFundraiseRequest;
import com.nukkad.investor.dto.FundraiseDto;
import com.nukkad.investor.dto.UpdateFundraiseRequest;
import com.nukkad.investor.entity.Fundraise;
import com.nukkad.investor.entity.FundraiseStatus;
import com.nukkad.investor.mapper.InvestorMapper;
import com.nukkad.investor.repository.FundraiseRepository;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers Fundraise ownership (founder-only), Startup.isRaising sync, and one-fundraise-per-startup. */
@ExtendWith(MockitoExtension.class)
class FundraiseServiceTest {

    @Mock private FundraiseRepository fundraiseRepository;
    @Mock private StartupRepository startupRepository;
    @Mock private StartupTeamMemberRepository teamMemberRepository;
    private final InvestorMapper investorMapper = new InvestorMapper();

    private FundraiseService service() {
        return new FundraiseService(fundraiseRepository, startupRepository, teamMemberRepository, investorMapper);
    }

    private Startup startup(String id) {
        return Startup.builder().id(id).name("Ledgerly").build();
    }

    private StartupTeamMember founder(String startupId, String userId) {
        return StartupTeamMember.builder().startupId(startupId).userId(userId).isFounder(true).status(StartupTeamMember.Status.ACTIVE).build();
    }

    private Fundraise fundraise(String id, String startupId) {
        return Fundraise.builder().id(id).startupId(startupId).targetAmount(100000).fundingStage(StartupStage.MVP).build();
    }

    @Test
    void founderCreatingAFundraiseMarksTheStartupAsRaising() {
        Startup startup = startup("s1");
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "founder1")).thenReturn(Optional.of(founder("s1", "founder1")));
        when(fundraiseRepository.existsByStartupId("s1")).thenReturn(false);
        when(fundraiseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(startupRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        FundraiseDto dto = service().create("founder1", new CreateFundraiseRequest("s1", 100000L, "MVP", "Hiring", 5000L));

        assertThat(dto.startupId()).isEqualTo("s1");
        assertThat(startup.isRaising()).isTrue();
        assertThat(dto.canManage()).isTrue();
    }

    @Test
    void nonFounderCannotCreateAFundraiseForSomeoneElsesStartup() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1")));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "stranger1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create("stranger1", new CreateFundraiseRequest("s1", 100000L, "MVP", null, null)))
                .isInstanceOf(ForbiddenException.class);

        verify(fundraiseRepository, never()).saveAndFlush(any());
    }

    @Test
    void aStartupCannotHaveTwoActiveFundraises() {
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1")));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "founder1")).thenReturn(Optional.of(founder("s1", "founder1")));
        when(fundraiseRepository.existsByStartupId("s1")).thenReturn(true);

        assertThatThrownBy(() -> service().create("founder1", new CreateFundraiseRequest("s1", 100000L, "MVP", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void creatingAFundraiseForANonexistentStartupIsRejected() {
        when(startupRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create("founder1", new CreateFundraiseRequest("ghost", 100000L, "MVP", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void founderCanUpdateTheirFundraise() {
        Fundraise fundraise = fundraise("f1", "s1");
        when(fundraiseRepository.findById("f1")).thenReturn(Optional.of(fundraise));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "founder1")).thenReturn(Optional.of(founder("s1", "founder1")));
        when(fundraiseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup("s1")));

        FundraiseDto dto = service().update("founder1", "f1", new UpdateFundraiseRequest(200000L, 50000L, null, null, null));

        assertThat(dto.targetAmount()).isEqualTo(200000L);
        assertThat(dto.amountRaised()).isEqualTo(50000L);
    }

    @Test
    void nonFounderCannotUpdateAnotherStartupsFundraise() {
        Fundraise fundraise = fundraise("f1", "s1");
        when(fundraiseRepository.findById("f1")).thenReturn(Optional.of(fundraise));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "stranger1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update("stranger1", "f1", new UpdateFundraiseRequest(1L, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);

        verify(fundraiseRepository, never()).saveAndFlush(any());
    }

    @Test
    void closingAFundraiseUnmarksTheStartupAsRaising() {
        Fundraise fundraise = fundraise("f1", "s1");
        Startup startup = startup("s1");
        startup.setRaising(true);
        when(fundraiseRepository.findById("f1")).thenReturn(Optional.of(fundraise));
        when(teamMemberRepository.findByStartupIdAndUserId("s1", "founder1")).thenReturn(Optional.of(founder("s1", "founder1")));
        when(fundraiseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startup));
        when(startupRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        FundraiseDto dto = service().close("founder1", "f1");

        assertThat(dto.status()).isEqualTo(FundraiseStatus.CLOSED.getLabel());
        assertThat(startup.isRaising()).isFalse();
    }
}
