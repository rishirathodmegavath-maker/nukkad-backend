package com.nukkad.investor.repository;

import com.nukkad.investor.entity.IntroRequest;
import com.nukkad.investor.entity.IntroRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IntroRequestRepository extends JpaRepository<IntroRequest, String> {
    boolean existsByRequesterIdAndRecipientIdAndStatus(String requesterId, String recipientId, IntroRequestStatus status);

    List<IntroRequest> findByRecipientIdOrderByCreatedAtDesc(String recipientId);
    List<IntroRequest> findByRequesterIdOrderByCreatedAtDesc(String requesterId);

    @Query("select case when count(r) > 0 then true else false end from IntroRequest r "
            + "where r.status = com.nukkad.investor.entity.IntroRequestStatus.ACCEPTED "
            + "and ((r.requesterId = :userA and r.recipientId = :userB) or (r.requesterId = :userB and r.recipientId = :userA))")
    boolean existsAcceptedIntroBetween(@Param("userA") String userA, @Param("userB") String userB);
}
