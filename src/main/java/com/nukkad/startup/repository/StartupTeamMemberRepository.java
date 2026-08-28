package com.nukkad.startup.repository;

import com.nukkad.startup.entity.StartupTeamMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StartupTeamMemberRepository extends JpaRepository<StartupTeamMember, String> {
    List<StartupTeamMember> findByStartupId(String startupId);

    /**
     * Serializes concurrent accept/reject decisions on the same join request: without this,
     * two simultaneous calls can both read status=PENDING before either commits, so both pass
     * the "already decided?" guard and both apply — the loser's decision should instead see the
     * already-applied status and be rejected with a clean error.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM StartupTeamMember m WHERE m.id = :id")
    Optional<StartupTeamMember> findByIdForUpdate(@Param("id") String id);
    List<StartupTeamMember> findByStartupIdAndStatus(String startupId, StartupTeamMember.Status status);
    Optional<StartupTeamMember> findByStartupIdAndUserId(String startupId, String userId);
    boolean existsByStartupIdAndUserId(String startupId, String userId);
    List<StartupTeamMember> findByStartupIdAndIsFounderTrue(String startupId);

    @Query("select case when count(m1) > 0 then true else false end from StartupTeamMember m1, StartupTeamMember m2 "
            + "where m1.startupId = m2.startupId and m1.status = com.nukkad.startup.entity.StartupTeamMember.Status.ACTIVE "
            + "and m2.status = com.nukkad.startup.entity.StartupTeamMember.Status.ACTIVE "
            + "and ((m1.userId = :userA and m2.userId = :userB) or (m1.userId = :userB and m2.userId = :userA))")
    boolean existsActiveTeamMembershipBetween(@Param("userA") String userA, @Param("userB") String userB);
}
