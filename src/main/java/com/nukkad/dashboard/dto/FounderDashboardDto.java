package com.nukkad.dashboard.dto;

import java.util.List;

/**
 * The founder dashboard: totals across every startup the user runs (as founder or admin), plus the same numbers for each
 * startup so the page never has to pick one. {@code primaryStartupId}/{@code primaryStartupName} are the first entry of
 * {@code startups} (founder roles before admin roles, then oldest first). {@code hasFoundedStartup} means "runs at least
 * one startup".
 */
public record FounderDashboardDto(
        boolean hasFoundedStartup,
        String primaryStartupId,
        String primaryStartupName,
        int startupCount,
        long profileViews,
        long investorInterests,
        long jobApplications,
        long followers,
        long eventRsvps,
        int profileCompletionPercent,
        List<StartupMetrics> startups
) {

    public record StartupMetrics(
            String id,
            String name,
            String logoUrl,
            String stage,
            boolean isRaising,
            String teamRole,
            long profileViews,
            long followers,
            long investorInterests,
            long jobApplications,
            long eventRsvps,
            int profileCompletionPercent
    ) {
    }
}
