package com.nukkad.admin.mapper;

import com.nukkad.admin.dto.AdminAuditLogDto;
import com.nukkad.admin.dto.AdminInvestorActivationDto;
import com.nukkad.admin.dto.AdminInvestorDto;
import com.nukkad.admin.dto.AdminReportDto;
import com.nukkad.admin.dto.AdminUserDto;
import com.nukkad.admin.dto.AdminWithdrawalDto;
import com.nukkad.common.audit.AuditLog;
import com.nukkad.investor.entity.Investor;
import com.nukkad.investor.entity.InvestorActivationRequest;
import com.nukkad.report.entity.Report;
import com.nukkad.user.entity.User;
import com.nukkad.wallet.entity.WithdrawalRequest;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AdminMapper {

    public AdminUserDto toDto(User user) {
        return new AdminUserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getHeadline(),
                user.getCollegeOrCompany(),
                user.getLocation(),
                user.getSecurityRoles().stream().map(Enum::name).collect(Collectors.toSet()),
                user.getStatus().name(),
                user.isEmailVerified(),
                user.isOnboardingCompleted(),
                user.getGoogleSubject() != null,
                user.getConnectionsCount(),
                user.getCreatedAt(),
                user.getLastActiveAt()
        );
    }

    /** {@code users} should contain every reporter/reportedUser/resolver id referenced across the
     *  page being mapped, fetched once via {@code findAllById} — avoids one query per report. */
    public AdminReportDto toDto(Report report, Map<String, User> users) {
        User reporter = users.get(report.getReporterId());
        User reported = users.get(report.getReportedUserId());
        User resolver = report.getResolvedByUserId() == null ? null : users.get(report.getResolvedByUserId());
        return new AdminReportDto(
                report.getId(),
                report.getReporterId(),
                reporter != null ? reporter.getName() : null,
                report.getReportedUserId(),
                reported != null ? reported.getName() : null,
                report.getCategory(),
                report.getConversationId(),
                report.getPostId(),
                report.getStatus().name(),
                report.getCreatedAt(),
                report.getResolvedByUserId(),
                resolver != null ? resolver.getName() : null,
                report.getResolvedAt(),
                report.getResolutionNote()
        );
    }

    /** {@code users} should contain every actor id referenced across the page being mapped. */
    public AdminAuditLogDto toDto(AuditLog entry, Map<String, User> users) {
        User actor = entry.getUserId() == null ? null : users.get(entry.getUserId());
        return new AdminAuditLogDto(
                entry.getId(),
                entry.getUserId(),
                actor != null ? actor.getName() : null,
                entry.getAction().name(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getDetails(),
                entry.getIpAddress(),
                entry.getCreatedAt()
        );
    }

    public AdminWithdrawalDto toDto(WithdrawalRequest request, User user) {
        return new AdminWithdrawalDto(
                request.getId(),
                request.getUserId(),
                user != null ? user.getName() : null,
                user != null ? user.getEmail() : null,
                request.getAmountMinorUnits(),
                request.getCurrency(),
                request.getNote(),
                request.getStatus().name(),
                request.getDecisionNote(),
                request.getDecidedByAdminId(),
                request.getCreatedAt(),
                request.getDecidedAt()
        );
    }

    public AdminInvestorActivationDto toDto(InvestorActivationRequest request, User requester, User reviewer) {
        return new AdminInvestorActivationDto(
                request.getId(),
                request.getRequesterUserId(),
                requester != null ? requester.getName() : null,
                requester != null ? requester.getEmail() : null,
                request.getStatus().name(),
                request.getInvestorType().getLabel(),
                request.getFirmName(),
                request.getThesis(),
                new HashSet<>(request.getSectors()),
                new HashSet<>(request.getStages()),
                new HashSet<>(request.getGeographies()),
                request.getTicketMin(),
                request.getTicketMax(),
                request.getPortfolioCount(),
                request.getWebsite(),
                request.getResultingProfileId(),
                request.getReviewNote(),
                reviewer != null ? reviewer.getName() : null,
                request.getCreatedAt(),
                request.getReviewedAt()
        );
    }

    /** {@code linkedProfileName} is the display name of the live investor account this catalog row is tied to
     *  (via {@link Investor#getLinkedInvestorProfileId()}), or null when it isn't linked to one. */
    public AdminInvestorDto toDto(Investor investor, String linkedProfileName) {
        return new AdminInvestorDto(
                investor.getId(),
                investor.getName(),
                investor.getInvestorType().getLabel(),
                investor.getDescription(),
                investor.getLocation(),
                investor.getWebsite(),
                investor.getLogoUrl(),
                new HashSet<>(investor.getSectors()),
                new HashSet<>(investor.getStages()),
                investor.getChequeMin(),
                investor.getChequeMax(),
                investor.isActive(),
                investor.isVisible(),
                investor.getLinkedInvestorProfileId(),
                linkedProfileName,
                investor.getCreatedByAdminId(),
                investor.getCreatedAt(),
                investor.getUpdatedAt()
        );
    }
}
