package com.nukkad.idea.repository;

import com.nukkad.idea.entity.IdeaInterest;
import com.nukkad.idea.entity.IdeaInterestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IdeaInterestRepository extends JpaRepository<IdeaInterest, String> {
    Optional<IdeaInterest> findByIdeaIdAndUserId(String ideaId, String userId);
    List<IdeaInterest> findByIdeaId(String ideaId);
    long countByIdeaId(String ideaId);
    long countByIdeaIdAndStatusNotIn(String ideaId, List<IdeaInterestStatus> excludedStatuses);
    void deleteByIdeaIdAndUserId(String ideaId, String userId);

    Page<IdeaInterest> findByIdeaIdOrderByCreatedAtDesc(String ideaId, Pageable pageable);
    Page<IdeaInterest> findByIdeaIdAndStatusOrderByCreatedAtDesc(String ideaId, IdeaInterestStatus status, Pageable pageable);

    /** Interest counts (excluding withdrawn/rejected) for a whole page of ideas in one query, instead of
     *  one {@link #countByIdeaIdAndStatusNotIn} call per row. */
    @Query("select i.ideaId as ideaId, count(i) as total from IdeaInterest i "
            + "where i.ideaId in :ideaIds and i.status not in :excludedStatuses group by i.ideaId")
    List<IdeaIdCount> countGroupedByIdeaIdInAndStatusNotIn(@Param("ideaIds") List<String> ideaIds,
                                                            @Param("excludedStatuses") List<IdeaInterestStatus> excludedStatuses);
}
