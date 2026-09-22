package com.nukkad.event.repository;

import com.nukkad.event.entity.EventAttendee;
import com.nukkad.startup.entity.StartupTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventAttendeeRepository extends JpaRepository<EventAttendee, String> {
    Optional<EventAttendee> findByEventIdAndUserId(String eventId, String userId);
    boolean existsByEventIdAndUserId(String eventId, String userId);
    long countByEventId(String eventId);
    long countByUserId(String userId);

    /** RSVPs to the events a startup is tagged on. The startup's own active team is left out: their RSVPs are not outside interest. */
    @Query("select count(a) from EventAttendee a "
            + "where a.eventId in (select es.eventId from EventStartup es where es.startupId = :startupId) "
            + "and a.userId not in (select m.userId from StartupTeamMember m where m.startupId = :startupId and m.status = :activeStatus)")
    long countRsvpsForStartupEvents(@Param("startupId") String startupId, @Param("activeStatus") StartupTeamMember.Status activeStatus);
    List<EventAttendee> findByEventIdOrderByRegisteredAtAsc(String eventId);
    void deleteByEventId(String eventId);
}
