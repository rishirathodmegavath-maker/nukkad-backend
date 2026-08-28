package com.nukkad.matching.service;

import com.nukkad.matching.dto.CofounderMatchDto;
import com.nukkad.matching.graph.CandidatePoolService;
import com.nukkad.matching.graph.CandidatePoolService.CandidatePool;
import com.nukkad.matching.graph.EgoNetworkService.EgoNetwork;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.User;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scenario from the product spec: Founder A has AI + Backend skills. Founder B has Marketing + Product
 * skills — zero overlap with A, but entirely complementary. Founder C has the exact same skills as A —
 * zero complementarity, pure overlap. Compatibility must rank B above C despite B sharing no skills at
 * all with A: this is the distinction between complementarity and similarity the feature exists for.
 *
 * Also covers the minimum-quality filter ({@link com.nukkad.matching.RecommendationWeights#COFOUNDER_MIN_QUALITY_SCORE}):
 * a candidate is only returned when its blended score clears the threshold AND it has at least one
 * real, human-readable reason — either condition failing alone is enough to exclude it. The scoring
 * and ranking formulas themselves are untouched by this filter.
 */
@ExtendWith(MockitoExtension.class)
class CompatibilityServiceTest {

    @Mock
    private CandidatePoolService candidatePoolService;
    @Mock
    private UserService userService;
    @Mock
    private UserMapper userMapper;

    private User user(String id, String... skills) {
        return User.builder().id(id).name(id).skills(Set.of(skills)).build();
    }

    private UserDto stubDto(String id) {
        return new UserDto(id, id, null, null, null, null, null, null, null, 0,
                Set.of(), Set.of(), Set.of(), Map.of(), null, null, null, null, 0, false, null,
                null, null, null, null, null, null, null, null, null, null, null, Set.of());
    }

    private CompatibilityService service() {
        return new CompatibilityService(candidatePoolService, userService, userMapper);
    }

    @Test
    void complementaryFounderPassesThresholdAndIdenticalFounderIsFilteredOut() {
        User viewer = user("A", "AI", "Backend");
        User complementary = user("B", "Marketing", "Product");
        User identical = user("C", "AI", "Backend");

        EgoNetwork network = new EgoNetwork(Map.of("B", 2, "C", 2), Set.of(), Map.of());
        CandidatePool pool = new CandidatePool(network, Set.of("B", "C"), List.of(complementary, identical));

        when(userService.getEntityOrThrow("A")).thenReturn(viewer);
        when(candidatePoolService.gather(viewer, 30)).thenReturn(pool);
        when(userMapper.toDto(complementary, "NONE", false)).thenReturn(stubDto("B"));

        List<CofounderMatchDto> matches = service().findMatches("A", 10);

        // C has zero complementarity, zero mutual connections, zero text/lookingFor overlap — score 0,
        // no reasons — correctly dropped entirely by the quality filter rather than shown as noise.
        assertThat(matches).extracting(m -> m.user().id()).containsExactly("B");

        CofounderMatchDto complementaryMatch = matches.get(0);
        assertThat(complementaryMatch.score()).isGreaterThanOrEqualTo(com.nukkad.matching.RecommendationWeights.COFOUNDER_MIN_QUALITY_SCORE);
        assertThat(complementaryMatch.reasons()).anyMatch(r -> r.contains("skills you don't have"));
    }

    @Test
    void lowMutualConnectionsAloneDoesNotClearQualityThreshold() {
        User viewer = user("A", "AI", "Backend");
        // Same skill category as viewer (no complementarity) so the only signal is a single mutual connection.
        User distantAcquaintance = user("B", "AI");

        EgoNetwork network = new EgoNetwork(Map.of("B", 2), Set.of("F1"), Map.of("B", Set.of("F1")));
        CandidatePool pool = new CandidatePool(network, Set.of("B"), List.of(distantAcquaintance));

        when(userService.getEntityOrThrow("A")).thenReturn(viewer);
        when(candidatePoolService.gather(viewer, 30)).thenReturn(pool);

        List<CofounderMatchDto> matches = service().findMatches("A", 10);

        // 1 mutual connection alone contributes 0.1 * 0.20 = 0.02 to the blended score — well below the
        // 0.20 threshold — so this candidate must not appear, even though it does have a real reason.
        assertThat(matches).isEmpty();
    }

    @Test
    void highProfileSimilarityAloneWithNoReasonsIsFilteredOut() {
        String sharedBio = "Building useful software products for underserved communities every day.";
        User viewer = User.builder().id("A").name("A").skills(Set.of("AI")).bio(sharedBio).build();
        // Identical bio text drives profile-similarity (cosine) to exactly 1.0, and same skill category
        // means zero complementarity — so the score clears the threshold purely on text similarity, but
        // buildReasons() has nothing concrete to report (no complementary skills, no mutual connections,
        // no lookingFor/openTo overlap). That must still be filtered out: a score alone isn't enough.
        User textTwin = User.builder().id("B").name("B").skills(Set.of("AI")).bio(sharedBio).build();

        EgoNetwork network = new EgoNetwork(Map.of("B", 2), Set.of(), Map.of());
        CandidatePool pool = new CandidatePool(network, Set.of("B"), List.of(textTwin));

        when(userService.getEntityOrThrow("A")).thenReturn(viewer);
        when(candidatePoolService.gather(viewer, 30)).thenReturn(pool);

        List<CofounderMatchDto> matches = service().findMatches("A", 10);

        assertThat(matches).isEmpty();
    }

    @Test
    void candidateWithNoCategorizableSkillsAndNoOtherSignalIsFilteredOut() {
        User viewer = user("A", "AI", "Backend");
        User noSkills = user("B");

        EgoNetwork network = new EgoNetwork(Map.of("B", 2), Set.of(), Map.of());
        CandidatePool pool = new CandidatePool(network, Set.of("B"), List.of(noSkills));

        when(userService.getEntityOrThrow("A")).thenReturn(viewer);
        when(candidatePoolService.gather(viewer, 30)).thenReturn(pool);

        List<CofounderMatchDto> matches = service().findMatches("A", 10);

        // No categorizable skills means no fabricated complementarity claim (score/reasons stay at
        // zero) — combined with the quality filter, such a candidate is dropped entirely rather than
        // shown with an empty reasons list.
        assertThat(matches).isEmpty();
    }

    @Test
    void widensCandidatePoolRequestByMultiplierForModerateLimit() {
        User viewer = user("A", "AI", "Backend");
        when(userService.getEntityOrThrow("A")).thenReturn(viewer);
        when(candidatePoolService.gather(viewer, 18))
                .thenReturn(new CandidatePool(new EgoNetwork(Map.of(), Set.of(), Map.of()), Set.of(), List.of()));

        service().findMatches("A", 6);

        // limit=6 -> requested pool size is 6 * COFOUNDER_CANDIDATE_POOL_MULTIPLIER (3) = 18, not the
        // bare limit — this is the fix: the quality filter now has more than exactly `limit` candidates
        // to work with before Top-K trims back down.
        verify(candidatePoolService).gather(viewer, 18);
        verify(candidatePoolService, never()).gather(viewer, 6);
    }

    @Test
    void capsWidenedCandidatePoolAtSensibleMaximumForLargeLimit() {
        User viewer = user("A", "AI", "Backend");
        when(userService.getEntityOrThrow("A")).thenReturn(viewer);
        when(candidatePoolService.gather(viewer, 60))
                .thenReturn(new CandidatePool(new EgoNetwork(Map.of(), Set.of(), Map.of()), Set.of(), List.of()));

        service().findMatches("A", 25);

        // limit=25 -> 25 * 3 = 75, which must be capped at COFOUNDER_CANDIDATE_POOL_MAX (60) so a large
        // requested limit can't blow up the TF-IDF corpus / scoring cost unboundedly.
        verify(candidatePoolService).gather(viewer, 60);
        verify(candidatePoolService, never()).gather(viewer, 75);
    }

    @Test
    void qualifyingCandidatesBeyondOriginalLimitSurfaceInTopKWhileOutputStillCapsAtLimit() {
        User viewer = user("A", "AI", "Backend");
        // 4 distinct, fully complementary founders — more than the requested limit of 2 — each scoring
        // 0.40 (complementarity 1.0 * weight 0.40), well clear of the quality threshold. With the old
        // limit-sized pool request (gather(viewer, 2)) at most 2 of these could ever have been fetched
        // in the first place; with the widened request (gather(viewer, 6)) all 4 are considered.
        User marketer = user("B1", "Marketing");
        User designer = user("B2", "Design");
        User productPerson = user("B3", "Product");
        User bizDev = user("B4", "Business");
        List<User> widenedCandidates = List.of(marketer, designer, productPerson, bizDev);

        EgoNetwork network = new EgoNetwork(Map.of("B1", 2, "B2", 2, "B3", 2, "B4", 2), Set.of(), Map.of());
        CandidatePool widenedPool = new CandidatePool(network, Set.of("B1", "B2", "B3", "B4"), widenedCandidates);

        when(userService.getEntityOrThrow("A")).thenReturn(viewer);
        when(candidatePoolService.gather(viewer, 6)).thenReturn(widenedPool);
        // All 4 tie at the same score, so exactly which 2 TopKSelector keeps is a tie-break detail this
        // test doesn't assert on — only 2 of these 4 stubs will actually be exercised.
        lenient().when(userMapper.toDto(marketer, "NONE", false)).thenReturn(stubDto("B1"));
        lenient().when(userMapper.toDto(designer, "NONE", false)).thenReturn(stubDto("B2"));
        lenient().when(userMapper.toDto(productPerson, "NONE", false)).thenReturn(stubDto("B3"));
        lenient().when(userMapper.toDto(bizDev, "NONE", false)).thenReturn(stubDto("B4"));

        List<CofounderMatchDto> matches = service().findMatches("A", 2);

        // The widened pool surfaced 4 qualifying candidates — more than the requested limit — but the
        // public API still returns at most `limit` results, drawn from that larger qualifying set.
        assertThat(matches).hasSize(2);
        assertThat(matches).allMatch(m -> widenedCandidates.stream().anyMatch(u -> u.getId().equals(m.user().id())));
        verify(candidatePoolService, never()).gather(viewer, 2);
    }
}
