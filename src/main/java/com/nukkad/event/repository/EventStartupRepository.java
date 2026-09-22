package com.nukkad.event.repository;

import com.nukkad.event.entity.EventStartup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventStartupRepository extends JpaRepository<EventStartup, String> {
    List<EventStartup> findByEventId(String eventId);
    List<EventStartup> findByStartupId(String startupId);
    Optional<EventStartup> findByEventIdAndStartupId(String eventId, String startupId);
    boolean existsByEventIdAndStartupId(String eventId, String startupId);
    void deleteByEventId(String eventId);
}
