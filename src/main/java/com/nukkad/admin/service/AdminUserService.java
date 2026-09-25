package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminUserDto;
import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.auth.service.AuthService;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.user.entity.AccountStatus;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.repository.UserSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminMapper adminMapper;
    private final AuthService authService;
    private final AuditService auditService;

    public AdminUserService(UserRepository userRepository, AdminMapper adminMapper,
                             AuthService authService, AuditService auditService) {
        this.userRepository = userRepository;
        this.adminMapper = adminMapper;
        this.authService = authService;
        this.auditService = auditService;
    }

    private User getEntityOrThrow(String id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<AdminUserDto> listUsers(String q, String role, String status, int page, int size) {
        SecurityRole roleEnum = parseOptionalEnum(role, SecurityRole.class, "role");
        AccountStatus statusEnum = parseOptionalEnum(status, AccountStatus.class, "status");
        Specification<User> spec = UserSpecifications.combine(
                UserSpecifications.adminSearch(q),
                UserSpecifications.hasSecurityRole(roleEnum),
                UserSpecifications.status(statusEnum)
        );
        Pageable pageable = PageRequest.of(page, AdminPaging.clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        return userRepository.findAll(spec, pageable).map(adminMapper::toDto);
    }

    @Transactional(readOnly = true)
    public AdminUserDto getUser(String id) {
        return adminMapper.toDto(getEntityOrThrow(id));
    }

    @Transactional
    public AdminUserDto updateStatus(String adminId, String targetUserId, UpdateUserStatusCommand command, String ip) {
        if (adminId.equals(targetUserId)) {
            throw new BadRequestException("You cannot change your own account status");
        }
        User target = getEntityOrThrow(targetUserId);
        AccountStatus newStatus = parseRequiredEnum(command.status(), AccountStatus.class, "status");
        AccountStatus oldStatus = target.getStatus();

        target.setStatus(newStatus);
        if (oldStatus != newStatus) {
            // Forward-only counter: every real status transition — suspend, disable, or
            // reactivate — bumps this, which immediately invalidates every access token issued
            // before this moment (see JwtAuthenticationFilter) regardless of remaining TTL. A
            // repeat call that doesn't actually change the status (already-suspended -> suspended)
            // intentionally does NOT bump it, so it can't accidentally sign out a session that was
            // established after the real transition already happened.
            target.setTokenVersion(target.getTokenVersion() + 1);
        }
        userRepository.save(target);

        if (newStatus == AccountStatus.SUSPENDED || newStatus == AccountStatus.DISABLED) {
            authService.revokeAllForUser(targetUserId);
        }

        Map<String, Object> details = new HashMap<>();
        details.put("oldStatus", oldStatus.name());
        details.put("newStatus", newStatus.name());
        if (command.reason() != null && !command.reason().isBlank()) {
            details.put("reason", command.reason());
        }
        auditService.log(adminId, AuditAction.ADMIN_USER_STATUS_CHANGED, "User", targetUserId, ip, details);

        return adminMapper.toDto(target);
    }

    @Transactional
    public AdminUserDto updateRole(String adminId, String targetUserId, UpdateUserRoleCommand command, String ip) {
        SecurityRole role = parseRequiredEnum(command.role(), SecurityRole.class, "role");
        if (role != SecurityRole.ADMIN) {
            throw new BadRequestException("Only the ADMIN role can be managed through this endpoint");
        }
        User target = getEntityOrThrow(targetUserId);
        boolean currentlyAdmin = target.getSecurityRoles().contains(SecurityRole.ADMIN);

        if (command.grant()) {
            if (currentlyAdmin) return adminMapper.toDto(target);
            target.getSecurityRoles().add(SecurityRole.ADMIN);
        } else {
            if (!currentlyAdmin) return adminMapper.toDto(target);
            // Same self-targeting rule as updateStatus, for the same reason: an admin can already
            // only reach this endpoint by holding the role, so a self-revoke is a way to accidentally
            // lock themselves out of the portal with no one else's action able to undo it in the
            // moment. The frontend already hides this control for the caller's own row; this is the
            // real boundary a direct API call would otherwise skip.
            if (adminId.equals(targetUserId)) {
                throw new BadRequestException("You cannot remove your own admin role");
            }
            if (userRepository.countByRole(SecurityRole.ADMIN) <= 1) {
                throw new BadRequestException("Cannot remove the last remaining admin");
            }
            target.getSecurityRoles().remove(SecurityRole.ADMIN);
        }
        // Granting or revoking ADMIN is a security-state change exactly like a status change: an
        // already-issued access token carries the OLD role set baked into its "roles" claim, so it
        // must stop being trusted immediately. Bumping tokenVersion reuses the exact forward-only
        // check JwtAuthenticationFilter already runs on every request — no new filter logic needed,
        // and (unlike suspend/disable) we deliberately do NOT revoke refresh tokens here: the
        // account itself isn't locked, so the existing refresh token should keep working and simply
        // mint a fresh access token next time, embedding the current roles and tokenVersion.
        target.setTokenVersion(target.getTokenVersion() + 1);
        userRepository.save(target);

        auditService.log(adminId, AuditAction.ADMIN_USER_ROLE_CHANGED, "User", targetUserId, ip,
                Map.of("role", role.name(), "grant", command.grant()));

        return adminMapper.toDto(target);
    }

    private <E extends Enum<E>> E parseOptionalEnum(String value, Class<E> type, String fieldName) {
        if (value == null || value.isBlank()) return null;
        return parseRequiredEnum(value, type, fieldName);
    }

    private <E extends Enum<E>> E parseRequiredEnum(String value, Class<E> type, String fieldName) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid " + fieldName + ": " + value);
        }
    }

    public record UpdateUserStatusCommand(String status, String reason) {}

    public record UpdateUserRoleCommand(String role, boolean grant) {}
}
