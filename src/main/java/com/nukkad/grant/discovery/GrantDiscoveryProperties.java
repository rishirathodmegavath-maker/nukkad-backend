package com.nukkad.grant.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Config for the scheduled AI grant-discovery pipeline. Off by default ({@code enabled=false}) --
 * see .env.example for what each env var does. {@code governments}/{@code topics} are the two axes
 * DiscoveryBatchCatalog crosses into one batch per scheduled run. {@code cron}/{@code
 * expirySweepCron} are also read directly by GrantDiscoveryScheduler's own {@code @Scheduled}
 * placeholders (Spring resolves both from the same underlying property); they're bound here too so
 * the actual configured values are inspectable/testable as plain data, not just as an annotation
 * string.
 */
@ConfigurationProperties(prefix = "nukkad.discovery")
public record GrantDiscoveryProperties(
        boolean enabled,
        String apiKey,
        String model,
        int maxNewGrantsPerRun,
        List<String> governments,
        List<String> topics,
        String cron,
        String expirySweepCron
) {
}
