package com.nukkad.admin.dto;

import java.time.Instant;
import java.util.Set;

/** Deliberately excludes passwordHash, googleSubject, refresh tokens and every other credential —
 *  an Admin needs to identify and act on an account, not authenticate as it. */
public record AdminUserDto(
        String id,
        String name,
        String email,
        String avatarUrl,
        String headline,
        String collegeOrCompany,
        String location,
        Set<String> roles,
        String status,
        boolean emailVerified,
        boolean onboardingCompleted,
        boolean googleLinked,
        int connectionsCount,
        Instant createdAt,
        Instant lastActiveAt
) {
}
