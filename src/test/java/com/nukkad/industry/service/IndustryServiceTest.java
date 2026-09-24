package com.nukkad.industry.service;

import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.grant.repository.GrantRepository;
import com.nukkad.industry.dto.IndustryDto;
import com.nukkad.investor.repository.InvestorRepository;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.repository.StartupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndustryServiceTest {

    @Mock private StartupRepository startupRepository;
    @Mock private InvestorRepository investorRepository;
    @Mock private GrantRepository grantRepository;

    private IndustryService service() {
        return new IndustryService(startupRepository, investorRepository, grantRepository);
    }

    private Startup startup(StartupStage stage, Instant createdAt) {
        return Startup.builder().id("s-" + createdAt).name("Rocket Labs").sector("AI").stage(stage)
                .needs(new HashSet<>()).createdAt(createdAt)
                .moderationStatus(com.nukkad.common.moderation.ModerationStatus.APPROVED).build();
    }

    @Test
    void industriesAreFoldedByCaseAndOrderedByStartupCount() {
        when(startupRepository.countBySector(true)).thenReturn(List.of(
                new Object[] {"AI", 3L}, new Object[] {"ai", 1L}, new Object[] {"Fintech", 2L}));
        when(investorRepository.findDistinctVisibleSectors()).thenReturn(List.of());

        var industries = service().listIndustries("viewer1", null);

        assertThat(industries).extracting("name", "slug", "startupCount").containsExactly(
                org.assertj.core.groups.Tuple.tuple("AI", "ai", 4L),
                org.assertj.core.groups.Tuple.tuple("Fintech", "fintech", 2L));
    }

    @Test
    void anAnonymousCallerOnlyCountsPublicStartups() {
        when(startupRepository.countBySector(false)).thenReturn(List.of());
        when(investorRepository.findDistinctVisibleSectors()).thenReturn(List.of());

        assertThat(service().listIndustries(null, null)).isEmpty();

        verify(startupRepository).countBySector(false);
        verify(startupRepository, never()).countBySector(true);
    }

    @Test
    void anIndustryWithOnlyInvestorInterestAndNoStartupsStillAppearsWithZeroStartupCount() {
        when(startupRepository.countBySector(true)).thenReturn(List.<Object[]>of(new Object[] {"AI", 3L}));
        when(investorRepository.findDistinctVisibleSectors()).thenReturn(List.of("SpaceTech"));

        var industries = service().listIndustries("viewer1", null);

        assertThat(industries).extracting("name", "slug", "startupCount").contains(
                org.assertj.core.groups.Tuple.tuple("SpaceTech", "spacetech", 0L));
    }

    @Test
    void qFiltersByCaseInsensitiveSubstring() {
        when(startupRepository.countBySector(true)).thenReturn(List.of(
                new Object[] {"AI", 3L}, new Object[] {"Fintech", 2L}));
        when(investorRepository.findDistinctVisibleSectors()).thenReturn(List.of());

        var industries = service().listIndustries("viewer1", "fin");

        assertThat(industries).extracting(IndustryDto::name).containsExactly("Fintech");
    }

    @Test
    void unknownSlugThrowsResourceNotFound() {
        when(startupRepository.countBySector(true)).thenReturn(List.<Object[]>of(new Object[] {"AI", 3L}));
        when(investorRepository.findDistinctVisibleSectors()).thenReturn(List.of());

        assertThatThrownBy(() -> service().getIndustry("does-not-exist", "viewer1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getIndustryComputesCountsAndStageDistributionFromRealMatchingStartups() {
        when(startupRepository.countBySector(true)).thenReturn(List.<Object[]>of(new Object[] {"AI", 3L}));
        when(investorRepository.findDistinctVisibleSectors()).thenReturn(List.of());

        Instant now = Instant.now();
        Startup recentMvp = startup(StartupStage.MVP, now.minus(Duration.ofDays(10)));
        Startup recentIdea = startup(StartupStage.IDEA, now.minus(Duration.ofDays(20)));
        Startup priorGrowth = startup(StartupStage.GROWTH, now.minus(Duration.ofDays(120)));
        when(startupRepository.findAll(any(Specification.class))).thenReturn(List.of(recentMvp, recentIdea, priorGrowth));
        when(investorRepository.count(any(Specification.class))).thenReturn(7L);
        when(grantRepository.count(any(Specification.class))).thenReturn(2L);

        var detail = service().getIndustry("ai", "viewer1");

        assertThat(detail.name()).isEqualTo("AI");
        assertThat(detail.slug()).isEqualTo("ai");
        assertThat(detail.startupCount()).isEqualTo(3);
        assertThat(detail.investorCount()).isEqualTo(7);
        assertThat(detail.grantCount()).isEqualTo(2);
        assertThat(detail.stageDistribution()).containsEntry("MVP", 1L).containsEntry("Idea", 1L).containsEntry("Growth", 1L);
        assertThat(detail.recentStartupCount()).isEqualTo(2);
        assertThat(detail.priorStartupCount()).isEqualTo(1);
    }
}
