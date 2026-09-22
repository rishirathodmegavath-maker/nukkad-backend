package com.nukkad.grant.discovery;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The fixed grid of batches the scheduler works through -- every (government, topic) pair from
 *  config, crossed. nextBatch() always picks whichever pair has gone longest without being
 *  *attempted* (or has never run), so a simple fixed daily schedule still eventually covers every
 *  pair instead of only ever hitting the first one. Deliberately keyed off the last attempt
 *  (any status), not the last success: a batch that keeps failing must still rotate back through
 *  on schedule, rather than permanently pinning itself as "oldest" and starving every other batch
 *  forever (see DiscoveryBatchCatalogTest). */
@Component
public class DiscoveryBatchCatalog {

    private final GrantDiscoveryProperties properties;
    private final GrantDiscoveryRunRepository runRepository;

    public DiscoveryBatchCatalog(GrantDiscoveryProperties properties, GrantDiscoveryRunRepository runRepository) {
        this.properties = properties;
        this.runRepository = runRepository;
    }

    public List<DiscoveryBatch> allBatches() {
        List<DiscoveryBatch> batches = new ArrayList<>();
        for (String government : properties.governments()) {
            for (String topic : properties.topics()) {
                batches.add(new DiscoveryBatch(government.trim(), topic.trim()));
            }
        }
        return batches;
    }

    public DiscoveryBatch nextBatch() {
        List<DiscoveryBatch> all = allBatches();
        if (all.isEmpty()) {
            throw new IllegalStateException("No discovery batches configured (nukkad.discovery.governments/topics)");
        }

        Map<String, Instant> lastAttemptByLabel = new HashMap<>();
        for (Object[] row : runRepository.findLastAttemptTimes()) {
            String label = new DiscoveryBatch((String) row[0], (String) row[1]).label();
            lastAttemptByLabel.put(label, (Instant) row[2]);
        }

        return all.stream()
                .min(Comparator.comparing(b -> lastAttemptByLabel.getOrDefault(b.label(), Instant.EPOCH)))
                .orElseThrow();
    }
}
