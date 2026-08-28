package com.nukkad.user.repository;

import com.nukkad.user.entity.UserFollow;
import com.nukkad.user.entity.UserFollowId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollowId> {
    boolean existsByFollowerIdAndFolloweeId(String followerId, String followeeId);
    void deleteByFollowerIdAndFolloweeId(String followerId, String followeeId);
}
