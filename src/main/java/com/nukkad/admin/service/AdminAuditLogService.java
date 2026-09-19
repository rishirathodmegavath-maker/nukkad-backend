package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminAuditLogDto;
import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditLog;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only by design — see AuditService for why audit history is never mutated or deleted,
 *  including through Admin. */
@Service
public class AdminAuditLogService {

    private final AuditService auditService;
    private final UserRepository userRepository;
    private final AdminMapper adminMapper;

    public AdminAuditLogService(AuditService auditService, UserRepository userRepository, AdminMapper adminMapper) {
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.adminMapper = adminMapper;
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLogDto> listLogs(String actorId, String action, String entityType,
                                            Instant from, Instant to, int page, int size) {
        AuditAction actionEnum = parseOptionalAction(action);
        Page<AuditLog> logs = auditService.listLogs(actorId, actionEnum, entityType, from, to, page, AdminPaging.clampSize(size));
        Map<String, User> users = fetchActors(logs.getContent());
        return logs.map(entry -> adminMapper.toDto(entry, users));
    }

    private AuditAction parseOptionalAction(String action) {
        if (action == null || action.isBlank()) return null;
        try {
            return AuditAction.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid action: " + action);
        }
    }

    private Map<String, User> fetchActors(List<AuditLog> logs) {
        Set<String> ids = new HashSet<>();
        for (AuditLog entry : logs) {
            if (entry.getUserId() != null) ids.add(entry.getUserId());
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
    }
}
