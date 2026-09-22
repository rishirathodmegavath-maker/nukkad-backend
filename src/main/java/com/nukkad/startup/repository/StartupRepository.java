package com.nukkad.startup.repository;

import com.nukkad.startup.entity.Startup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StartupRepository extends JpaRepository<Startup, String>, JpaSpecificationExecutor<Startup> {
    long countByChapterId(String chapterId);

    /**
     * Rows of (sector, count) over the startups discovery shows: not removed, approved, and only public ones unless the
     * viewer is signed in. Grouped on the exact stored text; the caller folds case variants together.
     */
    @Query("select s.sector, count(s) from Startup s "
            + "where s.sector is not null and trim(s.sector) <> '' and s.removedByAdmin = false "
            + "and s.moderationStatus = com.nukkad.common.moderation.ModerationStatus.APPROVED "
            + "and (:includeMembersOnly = true or s.visibility = com.nukkad.startup.entity.StartupVisibility.PUBLIC) "
            + "group by s.sector")
    List<Object[]> countBySector(@Param("includeMembersOnly") boolean includeMembersOnly);

    /** Feeds the chapter "recent activity" list — pageable's Sort (createdAt desc) determines order. */
    List<Startup> findByChapterId(String chapterId, Pageable pageable);
}
