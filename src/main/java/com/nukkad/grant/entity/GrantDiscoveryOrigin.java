package com.nukkad.grant.entity;

/** Distinguishes a grant a person (member or admin) entered from one the scheduled AI
 *  grant-discovery pipeline auto-published (see com.nukkad.grant.discovery.GrantDiscoveryService).
 *  Purely an internal/admin-panel distinction -- never set from a client-facing request. */
public enum GrantDiscoveryOrigin {
    MANUAL, AI_DISCOVERY
}
