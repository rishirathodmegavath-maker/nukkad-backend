package com.nukkad.user.repository;

import com.nukkad.user.entity.AccountStatus;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);

    /** Takes a row lock on the user, to serialise operations that must not run twice at once for the same user. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") String id);
    boolean existsByEmail(String email);
    Optional<User> findByGoogleSubject(String googleSubject);
    long countByChapterId(String chapterId);
    long countByChapterIdAndIdNot(String chapterId, String excludedUserId);

    /** Feeds the chapter "recent activity" list — pageable's Sort (chapterJoinedAt desc) determines order. */
    List<User> findByChapterIdAndChapterJoinedAtIsNotNull(String chapterId, Pageable pageable);

    /**
     * Login writes this on every request, including concurrent logins for the same account
     * (multiple tabs/devices). Loading the full User entity and saving it re-writes every mapped
     * column (Hibernate doesn't generate a partial UPDATE without @DynamicUpdate), which widens
     * the row lock enough that two such logins can deadlock each other. A single-column atomic
     * update touches only what actually changed and carries no such risk.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastActiveAt = :now WHERE u.id = :id")
    void touchLastActiveAt(@Param("id") String id, @Param("now") Instant now);

    /** Bulk skill fetch for a candidate pool — avoids N+1 lazy-loading `User.skills` per candidate
     *  when scoring many users at once. Each row is {@code [user_id, skill]}. */
    @Query(value = "select user_id, skill from user_skills where user_id in :userIds", nativeQuery = true)
    List<Object[]> findSkillsByUserIds(@Param("userIds") Collection<String> userIds);

    long countByStatus(AccountStatus status);

    @Query("SELECT COUNT(u) FROM User u JOIN u.securityRoles r WHERE r = :role")
    long countByRole(@Param("role") SecurityRole role);

    /**
     * Single-column projection by primary key — used on every authenticated request by
     * {@link com.nukkad.security.JwtAuthenticationFilter} to detect a revoked access token.
     * Deliberately not a full {@code findById}: loading the whole entity (and its eager
     * {@code securityRoles} collection) per request would be needlessly heavier than this
     * PK-indexed scalar lookup, which is the cheapest possible per-request DB round trip.
     */
    @Query("SELECT u.tokenVersion FROM User u WHERE u.id = :id")
    Optional<Integer> findTokenVersionById(@Param("id") String id);
}
