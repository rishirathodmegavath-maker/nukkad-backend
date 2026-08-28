package com.nukkad.event.repository;

import com.nukkad.event.entity.EventAttendee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventAttendeeRepository extends JpaRepository<EventAttendee, String> {
    Optional<EventAttendee> findByEventIdAndUserId(String eventId, String userId);
    boolean existsByEventIdAndUserId(String eventId, String userId);
    long countByEventId(String eventId);
    List<EventAttendee> findByEventIdOrderByRegisteredAtAsc(String eventId);
    void deleteByEventId(String eventId);
}
