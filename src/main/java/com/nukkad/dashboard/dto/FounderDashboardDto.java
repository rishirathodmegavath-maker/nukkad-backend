package com.nukkad.dashboard.dto;

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
        int profileCompletionPercent
) {
}
