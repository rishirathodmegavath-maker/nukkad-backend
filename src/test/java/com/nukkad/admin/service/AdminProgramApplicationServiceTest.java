package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminChangeProgramApplicationStatusRequest;
import com.nukkad.admin.dto.AdminProgramApplicationDto;
import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.program.entity.ProgramApplication;
import com.nukkad.program.entity.ProgramApplicationStatus;
import com.nukkad.program.repository.ProgramApplicationRepository;
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
class AdminProgramApplicationServiceTest {

    @Mock private ProgramApplicationRepository programApplicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    private final AdminMapper adminMapper = new AdminMapper();

    private AdminProgramApplicationService service() {
        return new AdminProgramApplicationService(programApplicationRepository, userRepository, adminMapper,
                auditService, notificationService);
    }

    private ProgramApplication submitted(String id, String userId) {
        return ProgramApplication.builder().id(id).applicantUserId(userId).program("SPARK")
                .status(ProgramApplicationStatus.SUBMITTED).answers(new java.util.HashMap<>()).build();
    }

    @Test
    void adminCanMoveASubmittedApplicationToUnderReview() {
        ProgramApplication application = submitted("a1", "user1");
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(any())).thenReturn(java.util.List.of(
                User.builder().id("user1").name("Asha Rao").email("asha@example.com").build()));

        AdminProgramApplicationDto dto = service().changeStatus("admin1", "a1",
                new AdminChangeProgramApplicationStatusRequest("UNDER_REVIEW", null), "127.0.0.1");

        assertThat(dto.status()).isEqualTo("UNDER_REVIEW");
        assertThat(dto.applicantName()).isEqualTo("Asha Rao");
        verify(auditService).log(eq("admin1"), any(), eq("ProgramApplication"), eq("a1"), any(), any());
        verify(notificationService).notify(eq("user1"), eq(NotificationType.program_application), anyString(), anyString(), eq("a1"), eq("admin1"));
    }

    @Test
    void adminCannotSetStatusToDraft() {
        ProgramApplication application = submitted("a1", "user1");
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service().changeStatus("admin1", "a1",
                new AdminChangeProgramApplicationStatusRequest("DRAFT", null), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void adminCannotSetStatusToWithdrawn() {
        ProgramApplication application = submitted("a1", "user1");
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service().changeStatus("admin1", "a1",
                new AdminChangeProgramApplicationStatusRequest("WITHDRAWN", null), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void adminCannotReviewAnApplicationThatIsStillADraft() {
        ProgramApplication draft = submitted("a1", "user1");
        draft.setStatus(ProgramApplicationStatus.DRAFT);
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service().changeStatus("admin1", "a1",
                new AdminChangeProgramApplicationStatusRequest("UNDER_REVIEW", null), "127.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void adminCannotReviewAWithdrawnApplication() {
        ProgramApplication withdrawn = submitted("a1", "user1");
        withdrawn.setStatus(ProgramApplicationStatus.WITHDRAWN);
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service().changeStatus("admin1", "a1",
                new AdminChangeProgramApplicationStatusRequest("UNDER_REVIEW", null), "127.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void selectingAnApplicantRecordsReviewerAndNote() {
        ProgramApplication application = submitted("a1", "user1");
        when(programApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
        when(programApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(any())).thenReturn(java.util.List.of());

        AdminProgramApplicationDto dto = service().changeStatus("admin1", "a1",
                new AdminChangeProgramApplicationStatusRequest("SELECTED", "Strong founder-market fit"), "127.0.0.1");

        assertThat(dto.status()).isEqualTo("SELECTED");
        assertThat(dto.adminNote()).isEqualTo("Strong founder-market fit");
        assertThat(dto.reviewedBy()).isEqualTo("admin1");
    }
}
