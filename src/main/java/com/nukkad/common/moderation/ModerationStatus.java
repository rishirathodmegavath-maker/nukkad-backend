package com.nukkad.common.moderation;

/**
 * Pre-publish review gate shared by Idea/Startup/Opportunity — identical semantics reused across
 * three otherwise-unrelated domains, mirroring how {@code AuditAction}/{@code AuditService} live
 * in {@code common.audit} rather than being duplicated per domain. Distinct from the separate,
 * reactive {@code removedByAdmin} flag on each entity: this gate runs once, before anything is
 * ever public; {@code removedByAdmin} can act on already-approved content at any later time.
 */
public enum ModerationStatus {
    PENDING, APPROVED, REJECTED
}
