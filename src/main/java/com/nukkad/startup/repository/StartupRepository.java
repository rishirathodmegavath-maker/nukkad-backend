package com.nukkad.startup.repository;

import com.nukkad.startup.entity.Startup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface StartupRepository extends JpaRepository<Startup, String>, JpaSpecificationExecutor<Startup> {
    long countByChapterId(String chapterId);

    /** Feeds the chapter "recent activity" list — pageable's Sort (createdAt desc) determines order. */
    List<Startup> findByChapterId(String chapterId, Pageable pageable);
}
