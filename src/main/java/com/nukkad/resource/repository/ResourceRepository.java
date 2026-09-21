package com.nukkad.resource.repository;

import com.nukkad.resource.entity.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, String>, JpaSpecificationExecutor<Resource> {
    long countByChapterId(String chapterId);

    /**
     * Every shelf-and-type combination that has at least one resource, as {category, type} pairs. The
     * category is null for a resource that was never filed on a shelf. Feeds the front-page mix.
     */
    @Query("select distinct r.category, r.type from Resource r")
    List<Object[]> findShelfAndTypeGroups();

    /** Feeds the chapter "recent activity" list — pageable's Sort (createdAt desc) determines order. */
    List<Resource> findByChapterId(String chapterId, Pageable pageable);
}
