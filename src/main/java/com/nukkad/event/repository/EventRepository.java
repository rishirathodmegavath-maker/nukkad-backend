package com.nukkad.event.repository;

import com.nukkad.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EventRepository extends JpaRepository<Event, String>, JpaSpecificationExecutor<Event> {
    long countByChapterId(String chapterId);
}
