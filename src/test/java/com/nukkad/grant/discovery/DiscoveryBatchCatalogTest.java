package com.nukkad.grant.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** B4 regression coverage: rotation must key off the last ATTEMPT (any status), not the last
 *  SUCCESS -- otherwise a batch that keeps failing never earns a "success" timestamp, stays tied
 *  at Instant.EPOCH forever, and (being first in iteration order) gets re-picked on every single
 *  tick, starving every other batch in the catalog permanently. */
@ExtendWith(MockitoExtension.class)
class DiscoveryBatchCatalogTest {

    @Mock private GrantDiscoveryRunRepository runRepository;

    private DiscoveryBatchCatalog catalog(List<String> governments, List<String> topics) {
        GrantDiscoveryProperties properties = new GrantDiscoveryProperties(
                true, "fake-key", "gemini-2.5-flash", 10, governments, topics, "0 17 3 * * *", "0 0 4 * * *");
        return new DiscoveryBatchCatalog(properties, runRepository);
    }

    @Test
    void picksFirstBatch_whenNothingHasEverBeenAttempted() {
        when(runRepository.findLastAttemptTimes()).thenReturn(List.of());
        DiscoveryBatchCatalog catalog = catalog(List.of("Central Government", "Maharashtra"), List.of("General"));

        assertThat(catalog.nextBatch()).isEqualTo(new DiscoveryBatch("Central Government", "General"));
    }

    @Test
    void picksBatchAttemptedLongestAgo() {
        Instant recent = Instant.now();
        Instant older = recent.minusSeconds(3600);
        when(runRepository.findLastAttemptTimes()).thenReturn(List.of(
                new Object[]{"Central Government", "General", recent},
                new Object[]{"Maharashtra", "General", older}
        ));
        DiscoveryBatchCatalog catalog = catalog(List.of("Central Government", "Maharashtra"), List.of("General"));

        assertThat(catalog.nextBatch()).isEqualTo(new DiscoveryBatch("Maharashtra", "General"));
    }

    @Test
    void repeatedlyFailingBatch_stillCountsAsAttempted_andDoesNotBlockOtherBatches() {
        // Batch A was just attempted (and, in reality, failed every time -- this method doesn't
        // even receive the status, which is the point of the fix: an attempt is an attempt).
        // Batch B has never been attempted at all, so it must be selected next, not A again.
        Instant justNow = Instant.now();
        // Collections.singletonList (not List.of) -- a single-varargs List.of(new Object[]{...})
        // is ambiguous for the compiler (E=Object[] vs E=Object spread across the array).
        when(runRepository.findLastAttemptTimes()).thenReturn(
                java.util.Collections.singletonList(new Object[]{"Central Government", "General", justNow}));
        DiscoveryBatchCatalog catalog = catalog(List.of("Central Government", "Maharashtra", "Karnataka"), List.of("General"));

        assertThat(catalog.nextBatch()).isEqualTo(new DiscoveryBatch("Maharashtra", "General"));

        // Once every OTHER batch has also been attempted at least once, rotation correctly returns
        // to whichever is now oldest -- including the repeatedly-failing one -- rather than that one
        // being permanently skipped either.
        when(runRepository.findLastAttemptTimes()).thenReturn(List.of(
                new Object[]{"Central Government", "General", justNow.minusSeconds(30)},
                new Object[]{"Maharashtra", "General", justNow},
                new Object[]{"Karnataka", "General", justNow}
        ));
        assertThat(catalog.nextBatch()).isEqualTo(new DiscoveryBatch("Central Government", "General"));
    }
}
