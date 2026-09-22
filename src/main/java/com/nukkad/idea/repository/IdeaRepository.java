package com.nukkad.idea.repository;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.idea.entity.Idea;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IdeaRepository extends JpaRepository<Idea, String>, JpaSpecificationExecutor<Idea> {
    long countByChapterId(String chapterId);
    long countByModerationStatus(ModerationStatus moderationStatus);

    /** Ideas that became the given startup point back at it; when that startup is deleted they go back to being plain ideas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Idea i set i.startupId = null where i.startupId = :startupId")
    int detachFromStartup(@Param("startupId") String startupId);

    /** Feeds the chapter "recent activity" list — pageable's Sort (createdAt desc) determines order. */
    List<Idea> findByChapterId(String chapterId, Pageable pageable);
}
