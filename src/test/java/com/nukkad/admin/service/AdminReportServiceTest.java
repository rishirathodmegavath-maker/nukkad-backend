package com.nukkad.admin.service;

import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.messaging.service.ConversationService;
import com.nukkad.report.service.ReportService;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @Mock private ReportService reportService;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    private final AdminMapper adminMapper = new AdminMapper();

    private AdminReportService service() {
        return new AdminReportService(reportService, userRepository, adminMapper, auditService);
    }

    /** Private conversations must never be readable from the admin side. This is a structural guard:
     *  if someone re-adds a message-reading path to the admin report service, or an admin-review
     *  reader to the conversation service, this fails. */
    @Test
    void adminSideHasNoWayToReadMessageContent() {
        assertThat(Arrays.stream(AdminReportService.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(name -> name.toLowerCase().contains("message") || name.toLowerCase().contains("conversation"));
        assertThat(Arrays.stream(ConversationService.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(name -> name.toLowerCase().contains("admin"));
        assertThat(Arrays.stream(AdminReportService.class.getDeclaredFields())
                .anyMatch(f -> f.getType() == ConversationService.class)).isFalse();
    }

    @Test
    void rejectsAnUnknownResolutionStatus() {
        assertThatThrownBy(() -> service().resolve("admin1", "r1", "banana", null, "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aReportCannotBeReopenedThroughTheResolveEndpoint() {
        assertThatThrownBy(() -> service().resolve("admin1", "r1", "OPEN", null, "1.2.3.4"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("RESOLVED or DISMISSED");
    }
}
