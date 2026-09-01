package com.nukkad.user.repository;

import com.nukkad.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByGoogleSubject(String googleSubject);
    long countByChapterId(String chapterId);

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
}
