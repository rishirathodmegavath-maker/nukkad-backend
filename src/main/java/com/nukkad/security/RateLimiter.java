package com.nukkad.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small in-memory, single-instance fixed-window rate limiter — matches this app's existing
 * single-instance-MVP precedent (e.g. the in-memory WebSocket broker) rather than pulling in
 * Redis/Bucket4j for one feature. Not shared across app instances if this is ever horizontally
 * scaled; acceptable for the current deployment shape.
 */
@Component
public class RateLimiter {

    private record Window(AtomicLong windowStart, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger sweepCounter = new AtomicInteger();

    /** Returns true if the call is allowed, false if the caller is over the limit for this window. */
    public boolean tryAcquire(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        if (sweepCounter.incrementAndGet() % 500 == 0) {
            sweepExpired(now, windowMillis);
        }
        Window window = windows.computeIfAbsent(key, k -> new Window(new AtomicLong(now), new AtomicInteger(0)));
        synchronized (window) {
            if (now - window.windowStart().get() > windowMillis) {
                window.windowStart().set(now);
                window.count().set(0);
            }
            return window.count().incrementAndGet() <= limit;
        }
    }

    private void sweepExpired(long now, long windowMillis) {
        windows.entrySet().removeIf(e -> now - e.getValue().windowStart().get() > windowMillis * 2);
    }
}
