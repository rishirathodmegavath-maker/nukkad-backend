package com.nukkad.idea.repository;

import com.nukkad.idea.entity.Idea;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface IdeaRepository extends JpaRepository<Idea, String>, JpaSpecificationExecutor<Idea> {
    long countByChapterId(String chapterId);

    /** Feeds the chapter "recent activity" list — pageable's Sort (createdAt desc) determines order. */
    List<Idea> findByChapterId(String chapterId, Pageable pageable);
}
