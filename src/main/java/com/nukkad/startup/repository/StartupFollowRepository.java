package com.nukkad.startup.repository;

import com.nukkad.startup.entity.StartupFollow;
import com.nukkad.startup.entity.StartupFollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StartupFollowRepository extends JpaRepository<StartupFollow, StartupFollowId> {
    boolean existsByUserIdAndStartupId(String userId, String startupId);
    void deleteByUserIdAndStartupId(String userId, String startupId);
    long countByStartupId(String startupId);

    /** Follower counts for a page of startups in one query: rows of (startupId, count); a startup nobody follows has no row. */
    @Query("select f.startupId, count(f) from StartupFollow f where f.startupId in :ids group by f.startupId")
    List<Object[]> countByStartupIds(@Param("ids") Collection<String> ids);

    /** Which of a page's worth of startups this viewer follows, in one query instead of one
     *  {@link #existsByUserIdAndStartupId} call per row. */
    @Query("select f.startupId from StartupFollow f where f.userId = :userId and f.startupId in :ids")
    List<String> findStartupIdsFollowedByUser(@Param("userId") String userId, @Param("ids") Collection<String> ids);
}
