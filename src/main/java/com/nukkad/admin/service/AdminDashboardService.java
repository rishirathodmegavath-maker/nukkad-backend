package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminDashboardDto;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.grant.repository.GrantRepository;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.investor.entity.InvestorActivationStatus;
import com.nukkad.investor.repository.InvestorActivationRequestRepository;
import com.nukkad.opportunity.repository.OpportunityRepository;
import com.nukkad.report.entity.ReportStatus;
import com.nukkad.report.service.ReportService;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.user.entity.AccountStatus;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.wallet.entity.WithdrawalStatus;
import com.nukkad.wallet.repository.WithdrawalRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final StartupRepository startupRepository;
    private final IdeaRepository ideaRepository;
    private final OpportunityRepository opportunityRepository;
    private final ReportService reportService;
    private final AdminAuditLogService adminAuditLogService;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final GrantRepository grantRepository;
    private final InvestorActivationRequestRepository investorActivationRequestRepository;

    public AdminDashboardService(UserRepository userRepository, StartupRepository startupRepository,
                                  IdeaRepository ideaRepository, OpportunityRepository opportunityRepository,
                                  ReportService reportService, AdminAuditLogService adminAuditLogService,
                                  WithdrawalRequestRepository withdrawalRequestRepository,
                                  GrantRepository grantRepository,
                                  InvestorActivationRequestRepository investorActivationRequestRepository) {
        this.userRepository = userRepository;
        this.startupRepository = startupRepository;
        this.ideaRepository = ideaRepository;
        this.opportunityRepository = opportunityRepository;
        this.reportService = reportService;
        this.adminAuditLogService = adminAuditLogService;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.grantRepository = grantRepository;
        this.investorActivationRequestRepository = investorActivationRequestRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardDto getDashboard() {
        return new AdminDashboardDto(
                userRepository.count(),
                userRepository.countByStatus(AccountStatus.ACTIVE),
                userRepository.countByStatus(AccountStatus.SUSPENDED),
                userRepository.countByStatus(AccountStatus.DISABLED),
                userRepository.countByRole(SecurityRole.FOUNDER),
                userRepository.countByRole(SecurityRole.INVESTOR),
                userRepository.countByRole(SecurityRole.CHAPTER_PRESIDENT),
                userRepository.countByRole(SecurityRole.ADMIN),
                startupRepository.count(),
                ideaRepository.count(),
                opportunityRepository.count(),
                opportunityRepository.countByClosedFalse(),
                reportService.countByStatus(ReportStatus.OPEN),
                ideaRepository.countByModerationStatus(ModerationStatus.PENDING)
                        + startupRepository.countByModerationStatus(ModerationStatus.PENDING)
                        + opportunityRepository.countByModerationStatus(ModerationStatus.PENDING)
                        + grantRepository.countByModerationStatus(ModerationStatus.PENDING),
                withdrawalRequestRepository.countByStatus(WithdrawalStatus.PENDING),
                investorActivationRequestRepository.countByStatus(InvestorActivationStatus.PENDING),
                adminAuditLogService.listLogs(null, null, null, null, null, 0, 10).getContent()
        );
    }
}
