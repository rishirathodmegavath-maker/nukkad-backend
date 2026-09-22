package com.nukkad.grant.discovery;

/** One (government-or-state, topic) combination the discovery prompt is built for -- see
 *  GrantDiscoveryPromptBuilder. */
public record DiscoveryBatch(String government, String topic) {

    /** Stable, human-readable key -- stored on GrantDiscoveryRun and used by DiscoveryBatchCatalog
     *  to tell batches apart when picking the least-recently-run one. */
    public String label() {
        return government + " · " + topic;
    }
}
