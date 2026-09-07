package com.nukkad.resource.repository;

import com.nukkad.resource.entity.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, String>, JpaSpecificationExecutor<Resource> {
    long countByChapterId(String chapterId);

    /** Feeds the chapter "recent activity" list — pageable's Sort (createdAt desc) determines order. */
    List<Resource> findByChapterId(String chapterId, Pageable pageable);
}
