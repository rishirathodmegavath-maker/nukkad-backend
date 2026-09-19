package com.nukkad.admin.dto;

import java.util.List;

public record AdminDashboardDto(
        long totalUsers,
        long activeUsers,
        long suspendedUsers,
        long disabledUsers,
        long founders,
        long investors,
        long chapterPresidents,
        long admins,
        long totalStartups,
        long totalIdeas,
        long totalOpportunities,
        long openOpportunities,
        long pendingReports,
        long pendingModeration,
        long pendingWithdrawals,
        long pendingInvestorActivations,
        List<AdminAuditLogDto> recentActivity
) {
}
