package com.nukkad.investor.repository;

import com.nukkad.investor.entity.IntroDirection;
import com.nukkad.investor.entity.IntroRequest;
import com.nukkad.investor.entity.IntroRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IntroRequestRepository extends JpaRepository<IntroRequest, String> {
    /** Requests between these two users, in either direction, that are still alive: waiting for an answer or accepted. */
    @Query("select r from IntroRequest r "
            + "where r.status in (com.nukkad.investor.entity.IntroRequestStatus.PENDING, com.nukkad.investor.entity.IntroRequestStatus.ACCEPTED) "
            + "and ((r.requesterId = :userA and r.recipientId = :userB) or (r.requesterId = :userB and r.recipientId = :userA))")
    List<IntroRequest> findActiveBetween(@Param("userA") String userA, @Param("userB") String userB);

    List<IntroRequest> findByRecipientIdOrderByCreatedAtDesc(String recipientId);
    List<IntroRequest> findByRequesterIdOrderByCreatedAtDesc(String requesterId);
    long countByRecipientIdAndDirection(String recipientId, IntroDirection direction);

    /** Requests about a startup in one direction, leaving out the ones the sender took back. */
    long countByStartupIdAndDirectionAndStatusNot(String startupId, IntroDirection direction, IntroRequestStatus status);

    @Query("select case when count(r) > 0 then true else false end from IntroRequest r "
            + "where r.status = com.nukkad.investor.entity.IntroRequestStatus.ACCEPTED "
            + "and ((r.requesterId = :userA and r.recipientId = :userB) or (r.requesterId = :userB and r.recipientId = :userA))")
    boolean existsAcceptedIntroBetween(@Param("userA") String userA, @Param("userB") String userB);
}
