package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminInvestorActivationDto;
import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.investor.dto.InvestorProfileDto;
import com.nukkad.investor.entity.InvestorActivationRequest;
import com.nukkad.investor.entity.InvestorActivationStatus;
import com.nukkad.investor.entity.InvestorType;
import com.nukkad.investor.repository.InvestorActivationRequestRepository;
import com.nukkad.investor.service.InvestorProfileService;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInvestorActivationServiceTest {

    @Mock private InvestorActivationRequestRepository activationRequestRepository;
    @Mock private InvestorProfileService investorProfileService;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    private final AdminMapper adminMapper = new AdminMapper();

    private AdminInvestorActivationService service() {
        return new AdminInvestorActivationService(activationRequestRepository, investorProfileService,
                userRepository, adminMapper, auditService, notificationService);
    }

    private InvestorActivationRequest pending(String id, String requesterId) {
        return InvestorActivationRequest.builder().id(id).requesterUserId(requesterId)
                .investorType(InvestorType.ANGEL).status(InvestorActivationStatus.PENDING).build();
    }

    @Test
    void approvingCreatesAnInvestorProfileAndMarksTheRequestApproved() {
        InvestorActivationRequest request = pending("r1", "user1");
        when(activationRequestRepository.findById("r1")).thenReturn(Optional.of(request));
        when(activationRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        InvestorProfileDto profile = new InvestorProfileDto("p1", "user1", null, "Angel", null, null,
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), null, null, 0, null, false, null, null);
        when(investorProfileService.create(eq("user1"), any())).thenReturn(profile);

        AdminInvestorActivationDto dto = service().approve("admin1", "r1", "127.0.0.1");

        assertThat(dto.status()).isEqualTo("APPROVED");
        assertThat(dto.resultingProfileId()).isEqualTo("p1");
        verify(auditService).log(eq("admin1"), any(), eq("InvestorActivationRequest"), eq("r1"), any(), any());
        verify(notificationService).notify(eq("user1"), eq(NotificationType.investor_activation), anyString(), anyString(), eq("p1"), eq("admin1"));
    }

    @Test
    void rejectingRequiresNoProfileCreationAndRecordsTheReason() {
        InvestorActivationRequest request = pending("r1", "user1");
        when(activationRequestRepository.findById("r1")).thenReturn(Optional.of(request));
        when(activationRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminInvestorActivationDto dto = service().reject("admin1", "r1", "insufficient track record", "127.0.0.1");

        assertThat(dto.status()).isEqualTo("REJECTED");
        assertThat(dto.reviewNote()).isEqualTo("insufficient track record");
        verify(investorProfileService, org.mockito.Mockito.never()).create(any(), any());
        verify(notificationService).notify(eq("user1"), eq(NotificationType.investor_activation), anyString(), anyString(), any(), eq("admin1"));
    }

    @Test
    void anAlreadyReviewedRequestCannotBeApprovedAgain() {
        InvestorActivationRequest reviewed = InvestorActivationRequest.builder().id("r1").requesterUserId("user1")
                .investorType(InvestorType.ANGEL).status(InvestorActivationStatus.APPROVED).build();
        when(activationRequestRepository.findById("r1")).thenReturn(Optional.of(reviewed));

        assertThatThrownBy(() -> service().approve("admin1", "r1", "127.0.0.1")).isInstanceOf(ConflictException.class);
        verify(investorProfileService, org.mockito.Mockito.never()).create(any(), any());
    }
}
