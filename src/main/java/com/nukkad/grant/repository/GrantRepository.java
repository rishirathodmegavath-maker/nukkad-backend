package com.nukkad.grant.repository;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.grant.entity.Grant;
import com.nukkad.grant.entity.GrantDiscoveryOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GrantRepository extends JpaRepository<Grant, String>, JpaSpecificationExecutor<Grant> {

    long countByModerationStatus(ModerationStatus moderationStatus);

    /** Dedup check #1 for GrantDiscoveryService -- the same official application URL means the
     *  same scheme, regardless of how its name is capitalised/phrased on different runs.
     *  Scoped to discoveryOrigin=AI_DISCOVERY so a manually/admin-created grant that happens to
     *  share a URL with something Gemini later discovers is never matched, and therefore never
     *  refreshed by the discovery pipeline (see GrantDiscoveryServiceTest). */
    Optional<Grant> findFirstByApplicationUrlIgnoreCaseAndDiscoveryOrigin(String applicationUrl, GrantDiscoveryOrigin discoveryOrigin);

    /** Dedup check #2, used when the URL alone didn't match (e.g. the AI cited a slightly
     *  different application vs. instructions page across runs). Same AI_DISCOVERY scoping as
     *  above, for the same reason. */
    Optional<Grant> findFirstByNameIgnoreCaseAndProviderIgnoreCaseAndDiscoveryOrigin(String name, String provider, GrantDiscoveryOrigin discoveryOrigin);

    /** Feeds the discovery pipeline's daily expiry sweep -- an AI-discovered grant whose deadline
     *  has passed is auto-hidden; a manually-entered one never is (admins manage those themselves). */
    List<Grant> findByDiscoveryOriginAndDeadlineBeforeAndRemovedByAdminFalse(GrantDiscoveryOrigin origin, Instant cutoff);
}
