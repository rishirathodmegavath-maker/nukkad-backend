package com.nukkad.event.repository;

import com.nukkad.event.entity.EventStartup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventStartupRepository extends JpaRepository<EventStartup, String> {
    List<EventStartup> findByEventId(String eventId);
    List<EventStartup> findByStartupId(String startupId);
    void deleteByEventId(String eventId);
}
