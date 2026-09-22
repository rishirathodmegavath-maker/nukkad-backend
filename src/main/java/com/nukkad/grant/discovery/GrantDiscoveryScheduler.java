package com.nukkad.grant.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The unattended trigger for grant discovery -- runs on a fixed, predictable nightly cadence.
 * AdminGrantDiscoveryController#runNow additionally lets an admin fire one batch on demand; both
 * paths call the same GrantDiscoveryService#runNextBatch. Both jobs here are no-ops until
 * nukkad.discovery.enabled is true, which stays false until GEMINI_API_KEY is actually configured.
 */
@Component
public class GrantDiscoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(GrantDiscoveryScheduler.class);

    private final GrantDiscoveryService discoveryService;
    private final GrantDiscoveryProperties properties;

    public GrantDiscoveryScheduler(GrantDiscoveryService discoveryService, GrantDiscoveryProperties properties) {
        this.discoveryService = discoveryService;
        this.properties = properties;
    }

    // Both cron expressions are resolved from nukkad.discovery.cron/expiry-sweep-cron -- which
    // application.yml populates from GRANT_DISCOVERY_CRON/GRANT_DISCOVERY_EXPIRY_SWEEP_CRON (see
    // .env.example) -- not hard-coded here; the literal fallback after the ':' only applies if
    // neither the env var nor the property is set at all.
    @Scheduled(cron = "${nukkad.discovery.cron:0 17 3 * * *}")
    public void discover() {
        if (!properties.enabled()) return;
        GrantDiscoveryRun result = discoveryService.runNextBatch();
        log.info("Grant discovery run finished: batch=[{} / {}] status={} found={} created={} updated={} rejected={}",
                result.getBatchGovernment(), result.getBatchTopic(), result.getStatus(),
                result.getSchemesFound(), result.getSchemesCreated(), result.getSchemesUpdated(), result.getSchemesRejected());
    }

    @Scheduled(cron = "${nukkad.discovery.expiry-sweep-cron:0 0 4 * * *}")
    public void sweepExpired() {
        if (!properties.enabled()) return;
        int hidden = discoveryService.hideExpiredAiDiscoveredGrants();
        if (hidden > 0) log.info("Grant discovery expiry sweep auto-hid {} expired AI-discovered grant(s)", hidden);
    }
}
