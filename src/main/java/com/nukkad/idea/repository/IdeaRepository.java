package com.nukkad.idea.repository;

import com.nukkad.idea.entity.Idea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IdeaRepository extends JpaRepository<Idea, String>, JpaSpecificationExecutor<Idea> {
    long countByChapterId(String chapterId);
}
