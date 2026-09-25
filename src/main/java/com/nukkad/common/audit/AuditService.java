package com.nukkad.common.audit;

import com.nukkad.common.paging.PageRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

/** details must never contain passwords, tokens, or other secrets — non-sensitive metadata only. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void log(String userId, AuditAction action, String entityType, String entityId, String ipAddress) {
        log(userId, action, entityType, entityId, ipAddress, null);
    }

    /** Same as the base overload, plus small non-sensitive metadata (e.g. an admin's reason, or an
     *  old-value/new-value pair) recorded in the log's JSON `details` column for later review. */
    public void log(String userId, AuditAction action, String entityType, String entityId, String ipAddress,
                     Map<String, ?> details) {
        String json = null;
        if (details != null && !details.isEmpty()) {
            try {
                json = objectMapper.writeValueAsString(details);
            } catch (JacksonException e) {
                log.warn("Failed to serialize audit details for action {}: {}", action, e.getMessage());
            }
        }
        AuditLog entry = AuditLog.builder()
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(json)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(entry);
    }

    /** Read-only, filtered access for the Admin audit-log view. Logs are never mutated or deleted
     *  through this or any other path — they're an immutable historical record. */
    @Transactional(readOnly = true)
    public Page<AuditLog> listLogs(String actorId, AuditAction action, String entityType, Instant from, Instant to,
                                    int page, int size) {
        Specification<AuditLog> spec = AuditLogSpecifications.combine(
                AuditLogSpecifications.actorId(actorId),
                AuditLogSpecifications.action(action),
                AuditLogSpecifications.entityType(entityType),
                AuditLogSpecifications.createdAfter(from),
                AuditLogSpecifications.createdBefore(to)
        );
        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogRepository.findAll(spec, pageable);
    }
}
