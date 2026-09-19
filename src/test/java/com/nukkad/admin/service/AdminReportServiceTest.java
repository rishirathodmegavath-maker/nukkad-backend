package com.nukkad.admin.service;

import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.messaging.dto.AdminMessageDto;
import com.nukkad.messaging.service.ConversationService;
import com.nukkad.report.entity.Report;
import com.nukkad.report.service.ReportService;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers the report evidence view: an admin reviewing a report can see what was actually said in
 *  the reported conversation, and that read is itself audit-logged. */
@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @Mock private ReportService reportService;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private ConversationService conversationService;
    private final AdminMapper adminMapper = new AdminMapper();

    private AdminReportService service() {
        return new AdminReportService(reportService, userRepository, adminMapper, auditService, conversationService);
    }

    private Report report(String id, String conversationId) {
        return Report.builder().id(id).reporterId("reporter1").reportedUserId("reported1")
                .conversationId(conversationId).category("Harassment").build();
    }

    @Test
    void aReportWithNoAttachedConversationReturnsNoMessagesAndIsNotAuditLogged() {
        when(reportService.getEntityOrThrow("r1")).thenReturn(report("r1", null));

        List<AdminMessageDto> messages = service().getConversationMessages("admin1", "r1", "1.2.3.4");

        assertThat(messages).isEmpty();
        verify(conversationService, never()).getMessagesForAdminReview(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void viewingAReportsConversationReturnsItsMessagesAndLogsTheRead() {
        when(reportService.getEntityOrThrow("r1")).thenReturn(report("r1", "conv1"));
        AdminMessageDto message = new AdminMessageDto("m1", "reported1", "TEXT", "you're fired", false, Instant.now());
        when(conversationService.getMessagesForAdminReview("conv1")).thenReturn(List.of(message));

        List<AdminMessageDto> messages = service().getConversationMessages("admin1", "r1", "1.2.3.4");

        assertThat(messages).containsExactly(message);
        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_ACTION), eq("Report"), eq("r1"), eq("1.2.3.4"), any());
    }
}
