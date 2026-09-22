package com.nukkad.grant.discovery;

import java.time.Instant;

/** Admin-panel-only read model for one run -- see AdminGrantDiscoveryController. */
public record GrantDiscoveryRunDto(
        String id,
        String batchGovernment,
        String batchTopic,
        String status,
        int schemesFound,
        int schemesCreated,
        int schemesUpdated,
        int schemesRejected,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
    public static GrantDiscoveryRunDto from(GrantDiscoveryRun run) {
        return new GrantDiscoveryRunDto(
                run.getId(), run.getBatchGovernment(), run.getBatchTopic(), run.getStatus().name(),
                run.getSchemesFound(), run.getSchemesCreated(), run.getSchemesUpdated(), run.getSchemesRejected(),
                run.getErrorMessage(), run.getStartedAt(), run.getFinishedAt()
        );
    }
}
