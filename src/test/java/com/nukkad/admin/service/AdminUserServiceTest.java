package com.nukkad.admin.service;

import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.auth.service.AuthService;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.user.entity.AccountStatus;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused on the token-version side effect that makes account status changes invalidate
 * already-issued access tokens immediately (see JwtAuthenticationFilterTest for the read side of
 * that mechanism). The self-status and last-admin guards are covered too since neither had a
 * dedicated unit test before this change.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthService authService;
    @Mock private AuditService auditService;

    private final AdminMapper adminMapper = new AdminMapper();

    private AdminUserService service() {
        return new AdminUserService(userRepository, adminMapper, authService, auditService);
    }

    private User user(String id, AccountStatus status, int tokenVersion, SecurityRole... roles) {
        return User.builder()
                .id(id).name("Test User").email(id + "@nukkad.test").passwordHash("hashed")
                .status(status).tokenVersion(tokenVersion)
                .securityRoles(new HashSet<>(Set.of(roles)))
                .build();
    }

    // ---- token version bump on real transitions ----

    @Test
    void suspendingAnActiveUserBumpsTokenVersionAndRevokesAllSessions() {
        User target = user("u1", AccountStatus.ACTIVE, 3, SecurityRole.USER);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().updateStatus("admin1", "u1",
                new AdminUserService.UpdateUserStatusCommand("SUSPENDED", "policy violation"), "127.0.0.1");

        assertThat(target.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(target.getTokenVersion()).isEqualTo(4); // bumped exactly once
        verify(authService).revokeAllForUser("u1");
    }

    @Test
    void disablingAnActiveUserBumpsTokenVersionAndRevokesAllSessions() {
        User target = user("u1", AccountStatus.ACTIVE, 0, SecurityRole.USER);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().updateStatus("admin1", "u1",
                new AdminUserService.UpdateUserStatusCommand("DISABLED", null), "127.0.0.1");

        assertThat(target.getStatus()).isEqualTo(AccountStatus.DISABLED);
        assertThat(target.getTokenVersion()).isEqualTo(1);
        verify(authService).revokeAllForUser("u1");
    }

    @Test
    void reactivatingASuspendedUserAlsoBumpsTokenVersion() {
        // Version is already 4 from a prior suspend; reactivating must move it forward again so
        // that a stale copy of the pre-suspension (v3) token — or even a token somehow minted at
        // the suspended-era version (v4) — can never become valid again, only a token freshly
        // issued by a NEW login (which will embed whatever the version is after this call).
        User target = user("u1", AccountStatus.SUSPENDED, 4, SecurityRole.USER);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().updateStatus("admin1", "u1",
                new AdminUserService.UpdateUserStatusCommand("ACTIVE", null), "127.0.0.1");

        assertThat(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(target.getTokenVersion()).isEqualTo(5);
        verify(authService, never()).revokeAllForUser(any()); // nothing to revoke — reactivating, not locking out
    }

    @Test
    void repeatingTheSameStatusDoesNotBumpTokenVersionAgain() {
        // Already SUSPENDED; a second identical call (e.g. a duplicate/retried request) must not
        // advance the version further — doing so would be harmless to security but is pointless
        // churn, and more importantly must never be relied upon to *not* accidentally look like a
        // fresh revocation of a session that was legitimately established after the real suspend.
        User target = user("u1", AccountStatus.SUSPENDED, 4, SecurityRole.USER);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().updateStatus("admin1", "u1",
                new AdminUserService.UpdateUserStatusCommand("SUSPENDED", null), "127.0.0.1");

        assertThat(target.getTokenVersion()).isEqualTo(4); // unchanged
        verify(authService).revokeAllForUser("u1"); // still re-asserted defensively, that's fine
    }

    @Test
    void auditLogRecordsTheStatusTransitionWithReason() {
        User target = user("u1", AccountStatus.ACTIVE, 0, SecurityRole.USER);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<java.util.Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(java.util.Map.class);

        service().updateStatus("admin1", "u1",
                new AdminUserService.UpdateUserStatusCommand("SUSPENDED", "spam"), "127.0.0.1");

        verify(auditService).log(eq("admin1"), any(), eq("User"), eq("u1"), any(), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).containsEntry("oldStatus", "ACTIVE").containsEntry("newStatus", "SUSPENDED")
                .containsEntry("reason", "spam");
    }

    // ---- ADMIN role grant/revoke also bumps token version ----

    @Test
    void grantingAdminToANonAdminUserBumpsTokenVersionAndAddsTheRole() {
        User target = user("u1", AccountStatus.ACTIVE, 2, SecurityRole.USER);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().updateRole("admin1", "u1", new AdminUserService.UpdateUserRoleCommand("ADMIN", true), "127.0.0.1");

        assertThat(target.getSecurityRoles()).contains(SecurityRole.ADMIN);
        assertThat(target.getTokenVersion()).isEqualTo(3);
    }

    @Test
    void revokingAdminFromAnAdminBumpsTokenVersionAndRemovesTheRole() {
        User target = user("u1", AccountStatus.ACTIVE, 0, SecurityRole.USER, SecurityRole.ADMIN);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.countByRole(SecurityRole.ADMIN)).thenReturn(2L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().updateRole("admin1", "u1", new AdminUserService.UpdateUserRoleCommand("ADMIN", false), "127.0.0.1");

        assertThat(target.getSecurityRoles()).doesNotContain(SecurityRole.ADMIN);
        assertThat(target.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void reGrantingAdminAfterRevocationBumpsTokenVersionAgainSoOldRevokedTokensStayDead() {
        // Simulates: grant (v0->1), revoke (v1->2), re-grant (v2->3). A token minted anywhere
        // before this final re-grant -- including one from the brief admin window between the
        // first grant and the revoke -- must never become valid again; only a token minted after
        // this call (embedding v3) authenticates going forward.
        User target = user("u1", AccountStatus.ACTIVE, 2, SecurityRole.USER);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().updateRole("admin1", "u1", new AdminUserService.UpdateUserRoleCommand("ADMIN", true), "127.0.0.1");

        assertThat(target.getSecurityRoles()).contains(SecurityRole.ADMIN);
        assertThat(target.getTokenVersion()).isEqualTo(3);
    }

    @Test
    void grantingAdminWhenAlreadyAdminDoesNotBumpTokenVersion() {
        User target = user("u1", AccountStatus.ACTIVE, 5, SecurityRole.USER, SecurityRole.ADMIN);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));

        service().updateRole("admin1", "u1", new AdminUserService.UpdateUserRoleCommand("ADMIN", true), "127.0.0.1");

        assertThat(target.getTokenVersion()).isEqualTo(5); // unchanged -- no-op
        verify(userRepository, never()).save(any());
    }

    @Test
    void revokingAdminWhenNotAdminDoesNotBumpTokenVersion() {
        User target = user("u1", AccountStatus.ACTIVE, 5, SecurityRole.USER);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));

        service().updateRole("admin1", "u1", new AdminUserService.UpdateUserRoleCommand("ADMIN", false), "127.0.0.1");

        assertThat(target.getTokenVersion()).isEqualTo(5); // unchanged -- no-op
        verify(userRepository, never()).save(any());
    }

    @Test
    void lastAdminProtectionLeavesTokenVersionUntouched() {
        // A different target from the acting admin, so this exercises the last-admin count guard in
        // isolation from the self-targeting guard (see anAdminCannotRevokeTheirOwnRoleEvenWhenOtherAdminsExist).
        User target = user("admin2", AccountStatus.ACTIVE, 7, SecurityRole.USER, SecurityRole.ADMIN);
        when(userRepository.findById("admin2")).thenReturn(Optional.of(target));
        when(userRepository.countByRole(SecurityRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service().updateRole("admin1", "admin2",
                new AdminUserService.UpdateUserRoleCommand("ADMIN", false), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);

        assertThat(target.getTokenVersion()).isEqualTo(7); // the throw happens before any mutation
        verify(userRepository, never()).save(any());
    }

    @Test
    void roleChangeAuditLogRecordsRoleAndGrantFlagWithTheActingAdminAsActor() {
        User target = user("u1", AccountStatus.ACTIVE, 0, SecurityRole.USER);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<java.util.Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(java.util.Map.class);

        service().updateRole("admin1", "u1", new AdminUserService.UpdateUserRoleCommand("ADMIN", true), "127.0.0.1");

        verify(auditService).log(eq("admin1"), any(), eq("User"), eq("u1"), any(), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).containsEntry("role", "ADMIN").containsEntry("grant", true);
    }

    @Test
    void forgedActorAndTokenVersionFieldsOnTheRoleCommandHaveNoEffect() {
        // UpdateUserRoleCommand only carries (role, grant) by construction -- there is no field
        // for a client to smuggle a tokenVersion, actorId, or adminId into. The acting admin is
        // always the adminId parameter, which the controller derives from the authenticated
        // principal, never from the request body. This test documents that invariant at the
        // service layer: passing an out-of-band adminId is the only way to change who the actor
        // is, and it always comes from the caller, not the command object.
        User target = user("u1", AccountStatus.ACTIVE, 9, SecurityRole.USER);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().updateRole("admin1", "u1", new AdminUserService.UpdateUserRoleCommand("ADMIN", true), "127.0.0.1");

        assertThat(target.getTokenVersion()).isEqualTo(10); // driven only by the real prior value
        verify(auditService).log(eq("admin1"), any(), any(), any(), any(), any());
    }

    // ---- self-protection guards ----

    @Test
    void adminCannotChangeTheirOwnStatus() {
        assertThatThrownBy(() -> service().updateStatus("admin1", "admin1",
                new AdminUserService.UpdateUserStatusCommand("SUSPENDED", null), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void soleAdminCannotRevokeTheirOwnLastAdminRole() {
        // The self-targeting guard now fires before the last-admin count is even checked (see
        // anAdminCannotRevokeTheirOwnRoleEvenWhenOtherAdminsExist) -- either guard alone would block
        // this exact case, so the outcome (rejected, no save) is what this test actually asserts.
        User target = user("admin1", AccountStatus.ACTIVE, 0, SecurityRole.USER, SecurityRole.ADMIN);
        when(userRepository.findById("admin1")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service().updateRole("admin1", "admin1",
                new AdminUserService.UpdateUserRoleCommand("ADMIN", false), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void anAdminCannotRevokeTheirOwnRoleEvenWhenOtherAdminsExist() {
        // Distinct from soleAdminCannotRevokeTheirOwnLastAdminRole above: this admin is NOT the last
        // one (countByRole would return 2, so the last-admin guard alone would let this through) --
        // the self-targeting check must independently block it, the same way updateStatus already
        // blocks a self-targeted status change regardless of how many other admins exist.
        User target = user("admin1", AccountStatus.ACTIVE, 0, SecurityRole.USER, SecurityRole.ADMIN);
        when(userRepository.findById("admin1")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service().updateRole("admin1", "admin1",
                new AdminUserService.UpdateUserRoleCommand("ADMIN", false), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).countByRole(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void roleEndpointRejectsAnyRoleOtherThanAdmin() {
        assertThatThrownBy(() -> service().updateRole("admin1", "u1",
                new AdminUserService.UpdateUserRoleCommand("FOUNDER", true), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void roleEndpointRejectsUnknownRoleString() {
        assertThatThrownBy(() -> service().updateRole("admin1", "u1",
                new AdminUserService.UpdateUserRoleCommand("SUPERADMIN", true), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }
}
