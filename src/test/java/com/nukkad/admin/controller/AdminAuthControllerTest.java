package com.nukkad.admin.controller;

import com.nukkad.auth.dto.ChangePasswordRequest;
import com.nukkad.auth.dto.PasswordResetConfirmDto;
import com.nukkad.auth.dto.PasswordResetRequestDto;
import com.nukkad.auth.service.AuthService;
import com.nukkad.common.email.MailProperties;
import com.nukkad.common.exception.ApiException;
import com.nukkad.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The emailed admin reset is switched off until a mail provider exists. These tests pin that: with
 * the switch off nothing is recorded or "sent", and changing the password from inside the portal is
 * never affected by it.
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {

    @Mock private AuthService authService;

    private final MockHttpServletRequest http = new MockHttpServletRequest();

    private AdminAuthController controller(boolean enabled) {
        return new AdminAuthController(authService,
                new MailProperties("no-reply@example.com", "https://app.example.com", "https://admin.example.com", enabled));
    }

    private static void assertUnavailable(Throwable thrown) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(e.getErrorCode()).isEqualTo("PASSWORD_RESET_UNAVAILABLE");
        });
    }

    @Test
    void statusReportsTheSwitch() {
        assertThat(controller(false).passwordResetStatus().data()).containsEntry("enabled", false);
        assertThat(controller(true).passwordResetStatus().data()).containsEntry("enabled", true);
    }

    @Test
    void requestingAResetWhileSwitchedOffIsRefusedAndRecordsNothing() {
        assertThatThrownBy(() -> controller(false).requestPasswordReset(new PasswordResetRequestDto("admin@example.com"), http))
                .satisfies(AdminAuthControllerTest::assertUnavailable);
        verifyNoInteractions(authService);
    }

    @Test
    void confirmingAResetWhileSwitchedOffIsRefusedToo() {
        assertThatThrownBy(() -> controller(false).confirmPasswordReset(new PasswordResetConfirmDto("token", "N3w-Passw0rd!x"), http))
                .satisfies(AdminAuthControllerTest::assertUnavailable);
        verifyNoInteractions(authService);
    }

    @Test
    void whenSwitchedOnTheRequestGoesThrough() {
        controller(true).requestPasswordReset(new PasswordResetRequestDto("admin@example.com"), http);
        verify(authService).requestAdminPasswordReset("admin@example.com", http.getRemoteAddr());
    }

    @Test
    void changingThePasswordInsideThePortalDoesNotDependOnTheSwitch() {
        AuthenticatedUser admin = new AuthenticatedUser("admin-1", "admin@example.com", Set.of("ADMIN"), 0);

        controller(false).changePassword(admin, new ChangePasswordRequest("Old-Passw0rd!x", "N3w-Passw0rd!x"), http);

        verify(authService).adminChangePassword("admin-1", "Old-Passw0rd!x", "N3w-Passw0rd!x", http.getRemoteAddr());
    }
}
