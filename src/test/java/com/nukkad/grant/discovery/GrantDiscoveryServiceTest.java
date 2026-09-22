package com.nukkad.grant.discovery;

import com.nukkad.grant.dto.DiscoveredGrantCandidate;
import com.nukkad.grant.entity.Grant;
import com.nukkad.grant.entity.GrantDiscoveryOrigin;
import com.nukkad.grant.entity.GrantProviderType;
import com.nukkad.grant.repository.GrantRepository;
import com.nukkad.grant.service.GrantService;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrantDiscoveryServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Mock private DiscoveryBatchCatalog batchCatalog;
    @Mock private GrantDiscoveryPromptBuilder promptBuilder;
    @Mock private GeminiClient geminiClient;
    @Mock private GrantService grantService;
    @Mock private GrantRepository grantRepository;
    @Mock private GrantDiscoveryRunRepository runRepository;
    @Mock private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GrantDiscoveryService service(int maxNewGrantsPerRun) {
        GrantDiscoveryProperties properties = new GrantDiscoveryProperties(
                true, "fake-key", "gemini-2.5-flash", maxNewGrantsPerRun,
                List.of("Central Government"), List.of("General startup funding"),
                "0 17 3 * * *", "0 0 4 * * *");
        return new GrantDiscoveryService(batchCatalog, promptBuilder, geminiClient, grantService, grantRepository,
                runRepository, userRepository, properties, objectMapper);
    }

    private DiscoveryBatch batch() {
        return new DiscoveryBatch("Central Government", "General startup funding");
    }

    private User admin(String id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private void stubBatchAndAdmin() {
        when(batchCatalog.nextBatch()).thenReturn(batch());
        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        when(userRepository.findByRoleOrderByCreatedAtAsc(eq(SecurityRole.ADMIN), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(admin("admin-1"))));
        // GrantDiscoveryRunRepository is a plain entity-returning save() -- Mockito's default answer
        // for that is null, not the argument, so every assertion on the returned run needs this.
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** No existing AI_DISCOVERY row matches on either dedup path -- the realistic "brand new
     *  candidate" case. Deliberately scoped to AI_DISCOVERY in the stub, mirroring exactly what the
     *  real repository query now does (see B1). */
    private void stubNoExistingAiGrant() {
        when(grantRepository.findFirstByApplicationUrlIgnoreCaseAndDiscoveryOrigin(anyString(), eq(GrantDiscoveryOrigin.AI_DISCOVERY)))
                .thenReturn(Optional.empty());
        when(grantRepository.findFirstByNameIgnoreCaseAndProviderIgnoreCaseAndDiscoveryOrigin(anyString(), anyString(), eq(GrantDiscoveryOrigin.AI_DISCOVERY)))
                .thenReturn(Optional.empty());
    }

    private String validCandidateJson() {
        return """
                [
                  {
                    "grantSchemeName": "Credit Guarantee Scheme for Startups",
                    "provider": "NCGTC",
                    "providerType": "Government",
                    "description": "Guarantees collateral-free loans to DPIIT-recognised startups.",
                    "fundingAmount": "Up to ₹20 crore guarantee cover",
                    "applicationDeadline": null,
                    "eligibilityCriteria": "DPIIT-recognised startups only",
                    "eligibleStages": ["Idea", "MVP", "Not-A-Real-Stage"],
                    "eligibleSectors": [],
                    "applicationUrl": "https://www.jansamarth.in",
                    "sourceUrl": "https://www.startupindia.gov.in/cgss"
                  }
                ]
                """;
    }

    private String twoCandidatesJson(String firstName, String secondName) {
        return """
                [
                  {
                    "grantSchemeName": "%s",
                    "provider": "NCGTC",
                    "providerType": "Government",
                    "description": "desc",
                    "fundingAmount": null,
                    "applicationDeadline": null,
                    "eligibilityCriteria": null,
                    "eligibleStages": [],
                    "eligibleSectors": [],
                    "applicationUrl": "https://www.jansamarth.in",
                    "sourceUrl": "https://www.startupindia.gov.in/cgss"
                  },
                  {
                    "grantSchemeName": "%s",
                    "provider": "Some Ministry",
                    "providerType": "Government",
                    "description": "desc",
                    "fundingAmount": null,
                    "applicationDeadline": null,
                    "eligibilityCriteria": null,
                    "eligibleStages": [],
                    "eligibleSectors": [],
                    "applicationUrl": "https://example.gov.in/apply",
                    "sourceUrl": "https://example.gov.in"
                  }
                ]
                """.formatted(firstName, secondName);
    }

    // ---------------------------------------------------------------------------------------
    // Basic create / validation (pre-existing coverage, updated for the new AI_DISCOVERY-scoped
    // repository method signatures)
    // ---------------------------------------------------------------------------------------

    @Test
    void createsNewGrant_whenCandidateIsValid() {
        stubBatchAndAdmin();
        stubNoExistingAiGrant();
        when(geminiClient.generateContent("prompt")).thenReturn(validCandidateJson());

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getStatus()).isEqualTo(DiscoveryRunStatus.SUCCESS);
        assertThat(run.getSchemesFound()).isEqualTo(1);
        assertThat(run.getSchemesCreated()).isEqualTo(1);
        assertThat(run.getSchemesRejected()).isEqualTo(0);

        ArgumentCaptor<DiscoveredGrantCandidate> captor = ArgumentCaptor.forClass(DiscoveredGrantCandidate.class);
        verify(grantService).createGrantFromDiscovery(captor.capture(), eq("admin-1"), eq(batch().label()));
        verify(grantService, never()).refreshGrantFromDiscovery(any(), any(), any(), any());

        DiscoveredGrantCandidate candidate = captor.getValue();
        assertThat(candidate.name()).isEqualTo("Credit Guarantee Scheme for Startups");
        assertThat(candidate.providerType()).isEqualTo(GrantProviderType.GOVERNMENT);
        assertThat(candidate.deadline()).isNull();
        // "Not-A-Real-Stage" is silently dropped, not fatal to the whole record.
        assertThat(candidate.eligibleStages()).containsExactlyInAnyOrder(StartupStage.IDEA, StartupStage.MVP);
    }

    @Test
    void rejectsCandidate_whenSourceUrlMissing() {
        stubBatchAndAdmin();
        String json = validCandidateJson().replace("\"sourceUrl\": \"https://www.startupindia.gov.in/cgss\"", "\"sourceUrl\": null");
        when(geminiClient.generateContent("prompt")).thenReturn(json);

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getSchemesCreated()).isEqualTo(0);
        assertThat(run.getSchemesRejected()).isEqualTo(1);
        verify(grantService, never()).createGrantFromDiscovery(any(), any(), any());
    }

    @Test
    void rejectsCandidate_whenProviderTypeUnrecognised() {
        stubBatchAndAdmin();
        String json = validCandidateJson().replace("\"providerType\": \"Government\"", "\"providerType\": \"Nonsense\"");
        when(geminiClient.generateContent("prompt")).thenReturn(json);

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getSchemesRejected()).isEqualTo(1);
        verify(grantService, never()).createGrantFromDiscovery(any(), any(), any());
    }

    @Test
    void marksRunFailed_whenNoAdminAccountExists() {
        when(batchCatalog.nextBatch()).thenReturn(batch());
        when(userRepository.findByRoleOrderByCreatedAtAsc(eq(SecurityRole.ADMIN), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getStatus()).isEqualTo(DiscoveryRunStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("No ADMIN account");
        verify(geminiClient, never()).generateContent(anyString());
    }

    @Test
    void capsNewGrantsPerRun() {
        stubBatchAndAdmin();
        stubNoExistingAiGrant();
        when(geminiClient.generateContent("prompt")).thenReturn(twoCandidatesJson("First Scheme", "Second Scheme"));

        GrantDiscoveryRun run = service(1).runNextBatch();

        assertThat(run.getSchemesFound()).isEqualTo(2);
        assertThat(run.getSchemesCreated()).isEqualTo(1);
        assertThat(run.getSchemesRejected()).isEqualTo(1);
        verify(grantService, times(1)).createGrantFromDiscovery(any(), any(), any());
    }

    @Test
    void hideExpiredAiDiscoveredGrants_hidesEachExpiredGrantOnly() {
        when(userRepository.findByRoleOrderByCreatedAtAsc(eq(SecurityRole.ADMIN), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(admin("admin-1"))));
        Grant g1 = Grant.builder().id("g1").build();
        Grant g2 = Grant.builder().id("g2").build();
        when(grantRepository.findByDiscoveryOriginAndDeadlineBeforeAndRemovedByAdminFalse(eq(GrantDiscoveryOrigin.AI_DISCOVERY), any()))
                .thenReturn(List.of(g1, g2));

        int hidden = service(10).hideExpiredAiDiscoveredGrants();

        assertThat(hidden).isEqualTo(2);
        verify(grantService).setRemovedByAdmin(eq("admin-1"), eq("g1"), eq(true), anyString(), anyString());
        verify(grantService).setRemovedByAdmin(eq("admin-1"), eq("g2"), eq(true), anyString(), anyString());
    }

    // ---------------------------------------------------------------------------------------
    // B1 -- never overwrite a manually-created grant
    // ---------------------------------------------------------------------------------------

    @Test
    void b1_updatesExistingAiDiscoveredGrant_whenApplicationUrlMatches() {
        stubBatchAndAdmin();
        when(geminiClient.generateContent("prompt")).thenReturn(validCandidateJson());
        Grant existingAiGrant = Grant.builder().id("grant-1").discoveryOrigin(GrantDiscoveryOrigin.AI_DISCOVERY).build();
        when(grantRepository.findFirstByApplicationUrlIgnoreCaseAndDiscoveryOrigin("https://www.jansamarth.in", GrantDiscoveryOrigin.AI_DISCOVERY))
                .thenReturn(Optional.of(existingAiGrant));

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getSchemesUpdated()).isEqualTo(1);
        assertThat(run.getSchemesCreated()).isEqualTo(0);
        verify(grantService).refreshGrantFromDiscovery(eq("grant-1"), any(), eq("admin-1"), eq(batch().label()));
        verify(grantService, never()).createGrantFromDiscovery(any(), any(), any());
    }

    @Test
    void b1_neverModifiesManualGrant_whenApplicationUrlMatches() {
        stubBatchAndAdmin();
        when(geminiClient.generateContent("prompt")).thenReturn(validCandidateJson());
        // The real repository query is scoped to discoveryOrigin=AI_DISCOVERY, so a MANUAL grant
        // sharing this exact URL is never returned by it -- proven here by stubbing that AI-scoped
        // lookup to correctly find nothing (as it would against a real DB with only a manual row at
        // this URL), and asserting the service falls through to creating a new candidate rather than
        // ever calling refreshGrantFromDiscovery.
        when(grantRepository.findFirstByApplicationUrlIgnoreCaseAndDiscoveryOrigin("https://www.jansamarth.in", GrantDiscoveryOrigin.AI_DISCOVERY))
                .thenReturn(Optional.empty());
        when(grantRepository.findFirstByNameIgnoreCaseAndProviderIgnoreCaseAndDiscoveryOrigin(
                "Credit Guarantee Scheme for Startups", "NCGTC", GrantDiscoveryOrigin.AI_DISCOVERY))
                .thenReturn(Optional.empty());

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getSchemesCreated()).isEqualTo(1);
        verify(grantService, never()).refreshGrantFromDiscovery(any(), any(), any(), any());
        verify(grantService).createGrantFromDiscovery(any(), eq("admin-1"), eq(batch().label()));
        // The two lookups the service issued were BOTH scoped to AI_DISCOVERY -- never a plain,
        // origin-agnostic lookup that could have matched the manual row.
        verify(grantRepository).findFirstByApplicationUrlIgnoreCaseAndDiscoveryOrigin("https://www.jansamarth.in", GrantDiscoveryOrigin.AI_DISCOVERY);
    }

    @Test
    void b1_neverModifiesManualGrant_whenNameAndProviderMatch() {
        stubBatchAndAdmin();
        when(geminiClient.generateContent("prompt")).thenReturn(validCandidateJson());
        // This time the URL-based lookup also finds nothing (as it would if the manual grant's
        // stored URL differs slightly), forcing the fallback name+provider lookup -- which must
        // likewise be AI_DISCOVERY-scoped and therefore also find nothing against a manual row.
        when(grantRepository.findFirstByApplicationUrlIgnoreCaseAndDiscoveryOrigin("https://www.jansamarth.in", GrantDiscoveryOrigin.AI_DISCOVERY))
                .thenReturn(Optional.empty());
        when(grantRepository.findFirstByNameIgnoreCaseAndProviderIgnoreCaseAndDiscoveryOrigin(
                "Credit Guarantee Scheme for Startups", "NCGTC", GrantDiscoveryOrigin.AI_DISCOVERY))
                .thenReturn(Optional.empty());

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getSchemesCreated()).isEqualTo(1);
        verify(grantService, never()).refreshGrantFromDiscovery(any(), any(), any(), any());
        verify(grantRepository).findFirstByNameIgnoreCaseAndProviderIgnoreCaseAndDiscoveryOrigin(
                "Credit Guarantee Scheme for Startups", "NCGTC", GrantDiscoveryOrigin.AI_DISCOVERY);
    }

    // ---------------------------------------------------------------------------------------
    // B2 -- validate DB string lengths; isolate one bad candidate from the rest of the batch
    // ---------------------------------------------------------------------------------------

    @Test
    void b2_rejectsOversizedName_withoutAttemptingToPersistIt() {
        stubBatchAndAdmin();
        stubNoExistingAiGrant();
        String tooLongName = "X".repeat(201); // Grant.name is VARCHAR(200)
        when(geminiClient.generateContent("prompt")).thenReturn(twoCandidatesJson(tooLongName, "Second Scheme"));

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getStatus()).isEqualTo(DiscoveryRunStatus.SUCCESS);
        assertThat(run.getSchemesFound()).isEqualTo(2);
        assertThat(run.getSchemesRejected()).isEqualTo(1);
        assertThat(run.getSchemesCreated()).isEqualTo(1);
        verify(grantService, times(1)).createGrantFromDiscovery(argThat(c -> c.name().equals("Second Scheme")), any(), any());
        verify(grantService, never()).createGrantFromDiscovery(argThat(c -> c.name().equals(tooLongName)), any(), any());
    }

    @Test
    void b2_rejectsOversizedFundingAmount() {
        stubBatchAndAdmin();
        // No stubNoExistingAiGrant() here: the oversized value is rejected by validate()'s length
        // check before findExisting() is ever consulted -- proven by Mockito's strict stubbing
        // (an unused stub on that lookup would fail this test).
        String json = validCandidateJson().replace("\"fundingAmount\": \"Up to ₹20 crore guarantee cover\"",
                "\"fundingAmount\": \"" + "X".repeat(201) + "\""); // Grant.fundingAmount is VARCHAR(200)
        when(geminiClient.generateContent("prompt")).thenReturn(json);

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getSchemesCreated()).isEqualTo(0);
        assertThat(run.getSchemesRejected()).isEqualTo(1);
    }

    @Test
    void b2_persistenceFailureForOneCandidate_doesNotAbortRemainingCandidates() {
        stubBatchAndAdmin();
        stubNoExistingAiGrant();
        when(geminiClient.generateContent("prompt")).thenReturn(twoCandidatesJson("First Scheme", "Second Scheme"));
        // Simulates a persistence-time failure GrantDiscoveryService's own validation didn't (and
        // can't fully) anticipate -- e.g. a DB constraint. Only the first candidate fails.
        doThrow(new RuntimeException("simulated DB failure"))
                .when(grantService).createGrantFromDiscovery(argThat(c -> c.name().equals("First Scheme")), any(), any());

        GrantDiscoveryRun run = service(10).runNextBatch();

        // The run itself is NOT aborted, and its counters accurately reflect what actually happened:
        // one candidate failed to persist (counted as rejected), the other succeeded.
        assertThat(run.getStatus()).isEqualTo(DiscoveryRunStatus.SUCCESS);
        assertThat(run.getSchemesFound()).isEqualTo(2);
        assertThat(run.getSchemesRejected()).isEqualTo(1);
        assertThat(run.getSchemesCreated()).isEqualTo(1);
        verify(grantService).createGrantFromDiscovery(argThat(c -> c.name().equals("Second Scheme")), any(), any());
    }

    // ---------------------------------------------------------------------------------------
    // B3 -- deadlines are interpreted in Asia/Kolkata, valid through end of that calendar day
    // ---------------------------------------------------------------------------------------

    @Test
    void b3_deadlineOfToday_inIndiaStandardTime_isNotTreatedAsExpired() {
        stubBatchAndAdmin();
        stubNoExistingAiGrant();
        String today = LocalDate.now(IST).toString();
        String json = validCandidateJson().replace("\"applicationDeadline\": null", "\"applicationDeadline\": \"" + today + "\"");
        when(geminiClient.generateContent("prompt")).thenReturn(json);

        GrantDiscoveryRun run = service(10).runNextBatch();

        // Regression guard for the exact bug: the old UTC-start-of-day comparison would have
        // rejected this as "already expired" for nearly the entire IST calendar day.
        assertThat(run.getSchemesCreated()).isEqualTo(1);
        assertThat(run.getSchemesRejected()).isEqualTo(0);
    }

    @Test
    void b3_deadlineOfYesterday_isTreatedAsExpired() {
        stubBatchAndAdmin();
        String yesterday = LocalDate.now(IST).minusDays(1).toString();
        String json = validCandidateJson().replace("\"applicationDeadline\": null", "\"applicationDeadline\": \"" + yesterday + "\"");
        when(geminiClient.generateContent("prompt")).thenReturn(json);

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getSchemesCreated()).isEqualTo(0);
        assertThat(run.getSchemesRejected()).isEqualTo(1);
    }

    @Test
    void b3_deadlineOfTomorrow_isNotExpired() {
        stubBatchAndAdmin();
        stubNoExistingAiGrant();
        String tomorrow = LocalDate.now(IST).plusDays(1).toString();
        String json = validCandidateJson().replace("\"applicationDeadline\": null", "\"applicationDeadline\": \"" + tomorrow + "\"");
        when(geminiClient.generateContent("prompt")).thenReturn(json);

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getSchemesCreated()).isEqualTo(1);
        assertThat(run.getSchemesRejected()).isEqualTo(0);
    }

    @Test
    void b3_deadlineWellInThePast_isStillTreatedAsExpired() {
        stubBatchAndAdmin();
        String pastDate = Instant.now().minus(30, ChronoUnit.DAYS).toString().substring(0, 10);
        String json = validCandidateJson().replace("\"applicationDeadline\": null", "\"applicationDeadline\": \"" + pastDate + "\"");
        when(geminiClient.generateContent("prompt")).thenReturn(json);

        GrantDiscoveryRun run = service(10).runNextBatch();

        assertThat(run.getSchemesCreated()).isEqualTo(0);
        assertThat(run.getSchemesRejected()).isEqualTo(1);
    }
}
