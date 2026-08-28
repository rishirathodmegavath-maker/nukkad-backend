package com.nukkad.event.repository;

import com.nukkad.event.entity.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, String>, JpaSpecificationExecutor<Event> {
    long countByChapterId(String chapterId);

    /**
     * Serializes concurrent RSVPs for the same event: without this, two simultaneous requests can
     * both read "capacity not yet reached" before either commits, oversubscribing the event.
     * Holding this lock for the duration of the RSVP transaction makes the read-then-write atomic.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") String id);
}
