package com.nukkad.common.audit;

import org.springframework.stereotype.Service;

/** details must never contain passwords, tokens, or other secrets — non-sensitive metadata only. */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String userId, AuditAction action, String entityType, String entityId, String ipAddress) {
        AuditLog entry = AuditLog.builder()
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(entry);
    }
}
