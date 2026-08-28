package com.nukkad.event.repository;

import com.nukkad.event.entity.Event;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public final class EventSpecifications {

    private EventSpecifications() {}

    @SafeVarargs
    public static Specification<Event> combine(Specification<Event>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<Event> search(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like)
        );
    }

    public static Specification<Event> chapterId(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("chapterId"), chapterId);
    }

    public static Specification<Event> organizerUserId(String organizerUserId) {
        if (organizerUserId == null || organizerUserId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("organizerUserId"), organizerUserId);
    }

    public static Specification<Event> upcoming(Boolean upcoming) {
        if (upcoming == null || !upcoming) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startAt"), Instant.now());
    }
}
