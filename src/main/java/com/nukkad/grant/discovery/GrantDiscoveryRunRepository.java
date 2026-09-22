package com.nukkad.grant.discovery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GrantDiscoveryRunRepository extends JpaRepository<GrantDiscoveryRun, String> {

    /** Feeds DiscoveryBatchCatalog's least-recently-*attempted* pick -- one row per (government,
     *  topic) batch, giving its most recent start time regardless of outcome (SUCCESS or FAILED
     *  both count as an attempt). Deliberately NOT filtered to successful runs only: a batch that
     *  keeps failing must still cycle back into rotation on its own schedule rather than being
     *  retried every single tick forever while every other batch starves (see
     *  DiscoveryBatchCatalogTest). A batch with no row here yet (never attempted) is treated by the
     *  catalog as due immediately. */
    @Query("select r.batchGovernment, r.batchTopic, max(r.startedAt) from GrantDiscoveryRun r "
            + "group by r.batchGovernment, r.batchTopic")
    List<Object[]> findLastAttemptTimes();
}
