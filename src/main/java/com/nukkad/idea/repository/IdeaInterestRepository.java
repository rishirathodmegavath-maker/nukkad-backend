package com.nukkad.idea.repository;

import com.nukkad.idea.entity.IdeaInterest;
import com.nukkad.idea.entity.IdeaInterestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdeaInterestRepository extends JpaRepository<IdeaInterest, String> {
    Optional<IdeaInterest> findByIdeaIdAndUserId(String ideaId, String userId);
    List<IdeaInterest> findByIdeaId(String ideaId);
    long countByIdeaId(String ideaId);
    void deleteByIdeaIdAndUserId(String ideaId, String userId);

    Page<IdeaInterest> findByIdeaIdOrderByCreatedAtDesc(String ideaId, Pageable pageable);
    Page<IdeaInterest> findByIdeaIdAndStatusOrderByCreatedAtDesc(String ideaId, IdeaInterestStatus status, Pageable pageable);
}
