package com.nukkad.investor.service;

import com.nukkad.admin.dto.AdminInvestorDto;
import com.nukkad.admin.dto.UpdateInvestorRequest;
import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.investor.dto.IntroRequestDto;
import com.nukkad.investor.dto.InvestorDto;
import com.nukkad.investor.dto.InvestorIntroductionResultDto;
import com.nukkad.investor.dto.RequestInvestorIntroductionRequest;
import com.nukkad.investor.entity.Investor;
import com.nukkad.investor.entity.InvestorIntroRequest;
import com.nukkad.investor.entity.InvestorIntroRequestStatus;
import com.nukkad.investor.entity.InvestorProfile;
import com.nukkad.investor.entity.InvestorType;
import com.nukkad.investor.mapper.InvestorMapper;
import com.nukkad.investor.repository.InvestorIntroRequestRepository;
import com.nukkad.investor.repository.InvestorProfileRepository;
import com.nukkad.investor.repository.InvestorRepository;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.service.StartupAccessPolicy;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers the access gate (the non-negotiable rule from the Investor spec), the LIVE-vs-RECORDED introduction
 *  branch that bridges the new catalog to the pre-existing live investor accounts, and admin CRUD validation. */
@ExtendWith(MockitoExtension.class)
class InvestorCatalogServiceTest {

    @Mock private InvestorRepository investorRepository;
    @Mock private InvestorIntroRequestRepository investorIntroRequestRepository;
    @Mock private InvestorProfileRepository investorProfileRepository;
    @Mock private StartupRepository startupRepository;
    @Mock private StartupAccessPolicy startupAccessPolicy;
    @Mock private IntroRequestService introRequestService;
    @Mock private UserRepository userRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private com.nukkad.common.audit.AuditService auditService;
    private final InvestorMapper investorMapper = new InvestorMapper();
    private final AdminMapper adminMapper = new AdminMapper();

    private InvestorCatalogService service() {
        return new InvestorCatalogService(investorRepository, investorIntroRequestRepository, investorProfileRepository,
                startupRepository, startupAccessPolicy, introRequestService, userRepository, investorMapper, adminMapper,
                fileStorageService, auditService);
    }

    private Investor investor(String id, boolean active, boolean visible, String linkedProfileId) {
        return Investor.builder().id(id).name("Peak Capital").investorType(InvestorType.VC)
                .sectors(new HashSet<>()).stages(new HashSet<>()).active(active).visible(visible)
                .linkedInvestorProfileId(linkedProfileId).createdByAdminId("admin1").build();
    }

    // ---- The non-negotiable access gate ----

    @Test
    void aUserWithNoActiveStartupCannotListInvestors() {
        when(startupAccessPolicy.hasActiveStartup("u1")).thenReturn(false);
        assertThatThrownBy(() -> service().list(null, null, null, null, null, null, null, "u1", 0, 20))
                .isInstanceOf(ForbiddenException.class);
        verify(investorRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    /** The global-search omnibox and the /search page both call this exact method (with a query term) for their
     *  "Investors" results — there is no separate search backend, so this same gate is what keeps investor
     *  results out of search for a viewer without an active startup. */
    @Test
    void searchingInvestorsByNameIsBlockedTheSameWayBrowsingIs() {
        when(startupAccessPolicy.hasActiveStartup("u1")).thenReturn(false);
        assertThatThrownBy(() -> service().list(null, null, null, null, null, null, "Peak Capital", "u1", 0, 5))
                .isInstanceOf(ForbiddenException.class);
        verify(investorRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void aUserWithNoActiveStartupCannotReadAnInvestorProfile() {
        when(startupAccessPolicy.hasActiveStartup("u1")).thenReturn(false);
        assertThatThrownBy(() -> service().get("inv1", "u1")).isInstanceOf(ForbiddenException.class);
        verify(investorRepository, never()).findById(anyString());
    }

    @Test
    void aUserWithNoActiveStartupCannotRequestAnIntroduction() {
        when(startupAccessPolicy.hasActiveStartup("u1")).thenReturn(false);
        var req = new RequestInvestorIntroductionRequest("s1", "hello");
        assertThatThrownBy(() -> service().requestIntroduction("u1", "inv1", req)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aFounderWithAnActiveStartupCanListInvestors() {
        when(startupAccessPolicy.hasActiveStartup("founder1")).thenReturn(true);
        when(investorRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(investor("inv1", true, true, null))));
        Page<InvestorDto> result = service().list(null, null, null, null, null, null, null, "founder1", 0, 20);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void aHiddenInvestorIsA404NotA403ToAFounder() {
        when(startupAccessPolicy.hasActiveStartup("founder1")).thenReturn(true);
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(investor("inv1", true, false, null)));
        assertThatThrownBy(() -> service().get("inv1", "founder1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aDeactivatedInvestorIsA404ToAFounder() {
        when(startupAccessPolicy.hasActiveStartup("founder1")).thenReturn(true);
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(investor("inv1", false, true, null)));
        assertThatThrownBy(() -> service().get("inv1", "founder1")).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- Facets: the real sector/stage vocabulary, since neither has a fixed enum like type ----

    @Test
    void aUserWithNoActiveStartupCannotReadFacets() {
        when(startupAccessPolicy.hasActiveStartup("u1")).thenReturn(false);
        assertThatThrownBy(() -> service().facets("u1")).isInstanceOf(ForbiddenException.class);
        verify(investorRepository, never()).findDistinctVisibleSectors();
    }

    @Test
    void facetsCollapsesCaseInsensitiveDuplicatesToOneSpellingAndSortsThem() {
        when(startupAccessPolicy.hasActiveStartup("founder1")).thenReturn(true);
        when(investorRepository.findDistinctVisibleSectors()).thenReturn(List.of("Fintech", "AI", "ai", "Healthtech"));
        when(investorRepository.findDistinctVisibleStages()).thenReturn(List.of("Series A", "seed", "Seed"));

        var facets = service().facets("founder1");

        // "AI" wins over "ai" only because it compares alphabetically first (a deterministic tiebreak) —
        // either matches the same investors either way, since the actual filter is case-insensitive.
        assertThat(facets.sectors()).containsExactly("AI", "Fintech", "Healthtech");
        assertThat(facets.stages()).containsExactly("Seed", "Series A");
    }

    // ---- Request introduction: LIVE (linked to a real investor account) vs RECORDED ----

    @Test
    void requestingAnIntroductionToALinkedInvestorReusesTheExistingLiveIntroPipeline() {
        when(startupAccessPolicy.hasActiveStartup("founder1")).thenReturn(true);
        Investor catalogRow = investor("inv1", true, true, "profile1");
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(catalogRow));
        Startup startup = Startup.builder().id("s1").name("Sportozen").build();
        when(startupAccessPolicy.requireReadable("s1", "founder1")).thenReturn(startup);
        when(startupAccessPolicy.canManage("s1", "founder1")).thenReturn(true);
        InvestorProfile linkedProfile = InvestorProfile.builder().id("profile1").userId("investorUser1").build();
        when(investorProfileRepository.findById("profile1")).thenReturn(Optional.of(linkedProfile));
        IntroRequestDto liveDto = new IntroRequestDto("ir1", "founder1", null, "investorUser1", null,
                "FOUNDER_TO_INVESTOR", "s1", "Sportozen", null, null, "hello", "Pending", null, null, null);
        when(introRequestService.create(eq("founder1"), any())).thenReturn(liveDto);

        InvestorIntroductionResultDto result = service().requestIntroduction("founder1", "inv1",
                new RequestInvestorIntroductionRequest("s1", "hello"));

        assertThat(result.kind()).isEqualTo("LIVE");
        assertThat(result.liveRequest()).isEqualTo(liveDto);
        assertThat(result.recordedRequest()).isNull();
        verify(investorIntroRequestRepository, never()).save(any());
    }

    @Test
    void requestingAnIntroductionToAnUnlinkedInvestorRecordsItForAnAdminInstead() {
        when(startupAccessPolicy.hasActiveStartup("founder1")).thenReturn(true);
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(investor("inv1", true, true, null)));
        Startup startup = Startup.builder().id("s1").name("Sportozen").build();
        when(startupAccessPolicy.requireReadable("s1", "founder1")).thenReturn(startup);
        when(startupAccessPolicy.canManage("s1", "founder1")).thenReturn(true);
        when(investorIntroRequestRepository.existsByInvestorIdAndStartupIdAndStatus("inv1", "s1", InvestorIntroRequestStatus.PENDING))
                .thenReturn(false);
        when(investorIntroRequestRepository.saveAndFlush(any())).thenAnswer(inv -> {
            InvestorIntroRequest r = inv.getArgument(0);
            r.setId("rec1");
            r.setCreatedAt(java.time.Instant.now());
            return r;
        });

        InvestorIntroductionResultDto result = service().requestIntroduction("founder1", "inv1",
                new RequestInvestorIntroductionRequest("s1", "hello"));

        assertThat(result.kind()).isEqualTo("RECORDED");
        assertThat(result.recordedRequest()).isNotNull();
        assertThat(result.liveRequest()).isNull();
        verify(introRequestService, never()).create(anyString(), any());
    }

    @Test
    void aSecondPendingRequestToTheSameUnlinkedInvestorForTheSameStartupIsRejected() {
        when(startupAccessPolicy.hasActiveStartup("founder1")).thenReturn(true);
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(investor("inv1", true, true, null)));
        Startup startup = Startup.builder().id("s1").name("Sportozen").build();
        when(startupAccessPolicy.requireReadable("s1", "founder1")).thenReturn(startup);
        when(startupAccessPolicy.canManage("s1", "founder1")).thenReturn(true);
        when(investorIntroRequestRepository.existsByInvestorIdAndStartupIdAndStatus("inv1", "s1", InvestorIntroRequestStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service().requestIntroduction("founder1", "inv1", new RequestInvestorIntroductionRequest("s1", "hello")))
                .isInstanceOf(ConflictException.class);
        verify(investorIntroRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void requestingOnBehalfOfAStartupYouDoNotManageIsForbiddenEvenIfYouManageAnotherOne() {
        // Satisfies the global gate via some OTHER startup, but cites one they don't manage.
        when(startupAccessPolicy.hasActiveStartup("founder1")).thenReturn(true);
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(investor("inv1", true, true, null)));
        Startup startup = Startup.builder().id("s-not-mine").name("Someone Else's Startup").build();
        when(startupAccessPolicy.requireReadable("s-not-mine", "founder1")).thenReturn(startup);
        when(startupAccessPolicy.canManage("s-not-mine", "founder1")).thenReturn(false);

        assertThatThrownBy(() -> service().requestIntroduction("founder1", "inv1",
                new RequestInvestorIntroductionRequest("s-not-mine", "hello")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requestingAnIntroductionToAHiddenInvestorIs404() {
        when(startupAccessPolicy.hasActiveStartup("founder1")).thenReturn(true);
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(investor("inv1", true, false, null)));
        assertThatThrownBy(() -> service().requestIntroduction("founder1", "inv1", new RequestInvestorIntroductionRequest("s1", "hello")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- Admin CRUD ----

    @Test
    void creatingWithAChequeMinAboveTheMaxIsRejected() {
        var in = new InvestorCatalogService.NewInvestor("Peak Capital", "VC", null, null, null,
                Set.of(), Set.of(), 5_000_000L, 1_000_000L, true, true, null,
                null, null, Set.of(), Set.of(), null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service().create("admin1", in, null, null)).isInstanceOf(BadRequestException.class);
        verify(investorRepository, never()).saveAndFlush(any());
    }

    @Test
    void creatingWithAnUnknownInvestorTypeIsRejected() {
        var in = new InvestorCatalogService.NewInvestor("Peak Capital", "Not A Real Type", null, null, null,
                Set.of(), Set.of(), null, null, true, true, null,
                null, null, Set.of(), Set.of(), null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service().create("admin1", in, null, null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void creatingWithALinkToAnInvestorProfileThatDoesNotExistIsRejected() {
        when(investorProfileRepository.existsById("ghost")).thenReturn(false);
        var in = new InvestorCatalogService.NewInvestor("Peak Capital", "VC", null, null, null,
                Set.of(), Set.of(), null, null, true, true, "ghost",
                null, null, Set.of(), Set.of(), null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service().create("admin1", in, null, null)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void creatingAValidCatalogInvestorSavesItAndAuditLogs() {
        when(investorRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Investor i = inv.getArgument(0);
            i.setId("inv1");
            i.setCreatedAt(java.time.Instant.now());
            i.setUpdatedAt(java.time.Instant.now());
            return i;
        });
        var in = new InvestorCatalogService.NewInvestor("Peak Capital", "VC", "Backs bold founders", "India", "https://peak.vc",
                Set.of("AI"), Set.of("Seed"), 500_000L, 5_000_000L, true, true, null,
                null, null, Set.of(), Set.of(), null, null, null, null, null, null, null, null, null, null);

        AdminInvestorDto dto = service().create("admin1", in, null, "127.0.0.1");

        assertThat(dto.name()).isEqualTo("Peak Capital");
        assertThat(dto.linkedInvestorProfileId()).isNull();
        verify(auditService).log(eq("admin1"), eq(com.nukkad.common.audit.AuditAction.ADMIN_INVESTOR_CREATED),
                eq("Investor"), eq("inv1"), eq("127.0.0.1"), any());
    }

    // ---- website/social link fields reject a non-http(s) scheme instead of storing it raw ----

    @Test
    void creatingWithAJavascriptSchemeWebsiteIsRejected() {
        var in = new InvestorCatalogService.NewInvestor("Peak Capital", "VC", null, null, "javascript:alert(1)",
                Set.of(), Set.of(), null, null, true, true, null,
                null, null, Set.of(), Set.of(), null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service().create("admin1", in, null, "127.0.0.1")).isInstanceOf(BadRequestException.class);
        verify(investorRepository, never()).saveAndFlush(any());
    }

    @Test
    void creatingWithADataSchemeSocialLinkIsRejected() {
        var in = new InvestorCatalogService.NewInvestor("Peak Capital", "VC", null, null, null,
                Set.of(), Set.of(), null, null, true, true, null,
                null, null, Set.of(), Set.of(), null, null,
                "data:text/html,<script>alert(1)</script>", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service().create("admin1", in, null, "127.0.0.1")).isInstanceOf(BadRequestException.class);
        verify(investorRepository, never()).saveAndFlush(any());
    }

    @Test
    void creatingWithABareDomainWebsiteGetsHttpsPutInFrontOfIt() {
        when(investorRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        var in = new InvestorCatalogService.NewInvestor("Peak Capital", "VC", null, null, "peak.vc",
                Set.of(), Set.of(), null, null, true, true, null,
                null, null, Set.of(), Set.of(), null, null, null, null, null, null, null, null, null, null);

        AdminInvestorDto dto = service().create("admin1", in, null, "127.0.0.1");

        assertThat(dto.website()).isEqualTo("https://peak.vc");
    }

    @Test
    void creatingWithABlankWebsiteLeavesItNullRatherThanRejectingTheRow() {
        when(investorRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        var in = new InvestorCatalogService.NewInvestor("Peak Capital", "VC", null, null, "   ",
                Set.of(), Set.of(), null, null, true, true, null,
                null, null, Set.of(), Set.of(), null, null, null, null, null, null, null, null, null, null);

        AdminInvestorDto dto = service().create("admin1", in, null, "127.0.0.1");

        assertThat(dto.website()).isNull();
    }

    @Test
    void updatingWebsiteToAVbscriptSchemeIsRejected() {
        Investor existing = investor("inv1", true, true, null);
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(existing));

        var request = new UpdateInvestorRequest(null, null, null, null, null, "vbscript:msgbox(1)", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service().update("admin1", "inv1", request, null)).isInstanceOf(BadRequestException.class);
        verify(investorRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatingASocialLinkToAProtocolRelativeUrlIsRejected() {
        Investor existing = investor("inv1", true, true, null);
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(existing));

        var request = new UpdateInvestorRequest(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, "//evil.example/x", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service().update("admin1", "inv1", request, null)).isInstanceOf(BadRequestException.class);
        verify(investorRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatingWebsiteToAValidUrlNormalizesAndSavesIt() {
        Investor existing = investor("inv1", true, true, null);
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(existing));
        when(investorRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateInvestorRequest(null, null, null, null, null, "peak.vc", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        AdminInvestorDto dto = service().update("admin1", "inv1", request, null);

        assertThat(dto.website()).isEqualTo("https://peak.vc");
    }

    @Test
    void updatingLeavesFieldsThatWerentSentUnchanged() {
        Investor existing = investor("inv1", true, true, null);
        existing.setLocation("India");
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(existing));
        when(investorRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateInvestorRequest(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, false, null, null, null, null, null, null, null, null, null);
        AdminInvestorDto dto = service().update("admin1", "inv1", request, null);

        assertThat(dto.location()).isEqualTo("India");
        assertThat(dto.visible()).isFalse();
        assertThat(dto.active()).isTrue();
    }

    @Test
    void deletingAnInvestorRemovesItsHostedLogo() {
        Investor existing = investor("inv1", true, true, null);
        existing.setLogoUrl("/uploads/investor-logos/x.png");
        when(investorRepository.findById("inv1")).thenReturn(Optional.of(existing));

        service().delete("admin1", "inv1", null);

        verify(investorRepository).delete(existing);
        verify(fileStorageService).deleteIfHosted("/uploads/investor-logos/x.png");
    }

    @Test
    void bulkDeleteRemovesEveryRequestedInvestorAndAuditLogsEachOne() {
        Investor a = investor("inv1", true, true, null);
        a.setLogoUrl("/uploads/investor-logos/a.png");
        Investor b = investor("inv2", true, true, null);
        when(investorRepository.findAllById(Set.of("inv1", "inv2"))).thenReturn(List.of(a, b));

        service().bulkDelete("admin1", List.of("inv1", "inv2"), "127.0.0.1");

        verify(investorRepository).delete(a);
        verify(investorRepository).delete(b);
        verify(fileStorageService).deleteIfHosted("/uploads/investor-logos/a.png");
        verify(auditService, org.mockito.Mockito.times(2))
                .log(eq("admin1"), eq(com.nukkad.common.audit.AuditAction.ADMIN_INVESTOR_DELETED), eq("Investor"), anyString(), eq("127.0.0.1"), any());
    }

    @Test
    void bulkDeleteIsAllOrNothingIfAnyRequestedIdDoesNotExist() {
        Investor a = investor("inv1", true, true, null);
        // Only inv1 comes back — inv2 doesn't exist.
        when(investorRepository.findAllById(Set.of("inv1", "inv2"))).thenReturn(List.of(a));

        assertThatThrownBy(() -> service().bulkDelete("admin1", List.of("inv1", "inv2"), null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(investorRepository, never()).delete(any(Investor.class));
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void bulkDeleteRejectsAnEmptyIdList() {
        assertThatThrownBy(() -> service().bulkDelete("admin1", List.of(), null))
                .isInstanceOf(BadRequestException.class);
        verify(investorRepository, never()).findAllById(any());
    }

    @Test
    void closingAnAlreadyClosedIntroRequestIsAConflict() {
        InvestorIntroRequest closed = InvestorIntroRequest.builder().id("rec1").investorId("inv1").requesterUserId("founder1")
                .startupId("s1").message("hi").status(InvestorIntroRequestStatus.CLOSED).build();
        when(investorIntroRequestRepository.findById("rec1")).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> service().closeIntroRequest("admin1", "rec1", null)).isInstanceOf(ConflictException.class);
    }
}
